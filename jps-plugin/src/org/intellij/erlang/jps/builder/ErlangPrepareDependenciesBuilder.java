/*
 * Copyright 2012-2015 Sergey Ignatov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.erlang.jps.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.GraphGenerator;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ErlangPrepareDependenciesBuilder extends TargetBuilder<ErlangSourceRootDescriptor, ErlangPrepareDependenciesTarget> {
  public static final String BUILD_ORDER_FILE_PATH = "erlang-builder/deps-tree.xml";
  private static final String NAME = "Prepare dependencies";
  private static final Logger LOG = Logger.getInstance(ErlangPrepareDependenciesBuilder.class);


  public ErlangPrepareDependenciesBuilder() {
    super(Collections.singletonList(ErlangPrepareDependenciesTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull ErlangPrepareDependenciesTarget target,
                    @NotNull DirtyFilesHolder<ErlangSourceRootDescriptor, ErlangPrepareDependenciesTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    LOG.info("Starting prepare dependencies");
    LOG.debug("Load project build order.");
    ErlangProjectBuildOrder projectBuildOrder = loadProjectBuildOrder(context);
    if (projectBuildOrder == null) {
      addPrepareDependenciesFailedMessage(context);
      return;
    }

    LOG.debug("Collect dirty files.");
    List<String> dirtyErlangFilePaths = new DirtyFileProcessor<String, ErlangPrepareDependenciesTarget>() {
      @Nullable
      @Override
      protected String getDirtyElement(@NotNull File file) throws IOException {
        return isSourceOrHeader(file) ? file.getAbsolutePath() : null;
      }
    }.collectDirtyElements(holder);

    JpsProject project = target.getProject();
    Map<String, ErlangModuleBuildOrder> moduleBuildOrders;
    if (dirtyErlangFilePaths.isEmpty()) {
      LOG.debug("There aren't dirty .erl or .hrl files.");
      moduleBuildOrders = ContainerUtil.newHashMap();
    }
    else {
      LOG.debug("Search dependent dirty files.");
      moduleBuildOrders = prepareBuildOrderForDirtyFiles(project, projectBuildOrder, dirtyErlangFilePaths);
    }
    LOG.debug("Start writing build orders to XML.");
    writeBuildOrderToXml(project, context, moduleBuildOrders);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return NAME;
  }

  @Nullable
  private static ErlangProjectBuildOrder loadProjectBuildOrder(@NotNull CompileContext context) {
    try {
      return ErlangBuilderXmlUtil.readFromXML(BUILD_ORDER_FILE_PATH, context, ErlangProjectBuildOrder.class);
    }
    catch (JDOMException e) {
      LOG.error("Can't read XML from " + BUILD_ORDER_FILE_PATH, e);
    }
    catch (IOException e) {
      LOG.warn("Can't read " + BUILD_ORDER_FILE_PATH, e);
    }
    return null;
  }

  private static boolean isSourceOrHeader(@NotNull File file) {
    return file.getName().endsWith(".erl") || file.getName().endsWith(".hrl");
  }

  private static Map<String, ErlangModuleBuildOrder> prepareBuildOrderForDirtyFiles(@NotNull JpsProject project,
                                                                                    @NotNull ErlangProjectBuildOrder dependencyTree,
                                                                                    @NotNull List<String> dirtyErlangFilePaths) {
    SortedModuleDependencyGraph graph = new SortedModuleDependencyGraph(dependencyTree);
    Set<String> allDirtyFiles = getAllDirtyFiles(dirtyErlangFilePaths, GraphGenerator.create(graph));
    return BuildOrdersCreator.createBuildOrders(project, dependencyTree.myNodes, allDirtyFiles);
  }

  private static void writeBuildOrderToXml(@NotNull JpsProject project,
                                           @NotNull CompileContext context,
                                           Map<String, ErlangModuleBuildOrder> buildOrdersForDirtyErlangModules) {
    for (JpsModule module : project.getModules()) {
      String moduleName = module.getName();
      ErlangModuleBuildOrder orderDescriptor = buildOrdersForDirtyErlangModules.get(moduleName);
      String fileName = ErlangBuilder.ORDERED_DIRTY_FILES_PATH_PREFIX + moduleName + ".xml";
      LOG.debug("Writing build orders for " + moduleName + " to file " + fileName);
      try {
        ErlangBuilderXmlUtil.writeToXML(fileName, orderDescriptor != null ? orderDescriptor : ErlangModuleBuildOrder.EMPTY, context);
      }
      catch (IOException e) {
        LOG.warn("Can't write " + fileName, e);
      }
    }
  }

  private static void addPrepareDependenciesFailedMessage(@NotNull CompileContext context) {
    context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.WARNING, "The project will be fully rebuilt due to errors."));
  }

  private static Set<String> getAllDirtyFiles(@NotNull List<String> dirtyFiles,
                                              @NotNull GraphGenerator<String> graph) {
    Set<String> allDirtyFiles = ContainerUtil.newHashSet();
    for (String node : dirtyFiles) {
      if (node != null) {
        findDirtyFiles(node, graph, allDirtyFiles);
      }
    }
    return allDirtyFiles;
  }

  private static void findDirtyFiles(@NotNull String filePath,
                                     @NotNull GraphGenerator<String> graph,
                                     @NotNull Set<String> dirtyFiles) {
    if (dirtyFiles.contains(filePath)) return;
    dirtyFiles.add(filePath);
    Iterator<String> childIterator = graph.getOut(filePath);
    while (childIterator.hasNext()) {
      findDirtyFiles(childIterator.next(), graph, dirtyFiles);
    }
  }

  @NotNull
  private static ErlangModuleBuildOrder getOrCreateModuleBuildOrder(@NotNull String moduleName,
                                                                    @NotNull Map<String, ErlangModuleBuildOrder> buildOrders) {
    ErlangModuleBuildOrder erlangModuleBuildOrder = buildOrders.get(moduleName);
    if (erlangModuleBuildOrder != null) {
      return erlangModuleBuildOrder;
    }
    buildOrders.put(moduleName, new ErlangModuleBuildOrder());
    return buildOrders.get(moduleName);
  }

  private static class SortedModuleDependencyGraph implements GraphGenerator.SemiGraph<String> {
    private final LinkedHashMap<String, List<String>> myNodePathsMap = ContainerUtil.newLinkedHashMap();

    public SortedModuleDependencyGraph(@NotNull ErlangProjectBuildOrder dependencyTree) {
      for (ErlangFileDescriptor node : dependencyTree.myNodes) {
        myNodePathsMap.put(node.myPath, node.myDependencies);
      }
    }

    @Override
    public Collection<String> getNodes() {
      return myNodePathsMap.keySet();
    }

    @Override
    public Iterator<String> getIn(String node) {
      return myNodePathsMap.get(node).iterator();
    }
  }

  private static class BuildOrdersCreator {
    private final Map<String, SourceRootInfo> mySourceInfoIndex;
    private final List<ErlangFileDescriptor> mySortedNodes;

    private BuildOrdersCreator(@NotNull JpsProject project,
                               @NotNull List<ErlangFileDescriptor> sortedNodes) {
      mySortedNodes = sortedNodes;
      mySourceInfoIndex = ContainerUtil.newHashMap();
      for (JpsModule myModule : project.getModules()) {
        for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> root : myModule.getSourceRoots(JavaSourceRootType.SOURCE)) {
          mySourceInfoIndex.put(root.getFile().getAbsolutePath(), new SourceRootInfo(myModule.getName(), false));
        }
        for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> root : myModule.getSourceRoots(JavaSourceRootType.TEST_SOURCE)) {
          mySourceInfoIndex.put(root.getFile().getAbsolutePath(), new SourceRootInfo(myModule.getName(), true));
        }
      }
    }

    @NotNull
    public static Map<String, ErlangModuleBuildOrder> createBuildOrders(@NotNull JpsProject project,
                                                                        @NotNull List<ErlangFileDescriptor> nodes,
                                                                        @NotNull Set<String> dirtySortedErlangFiles) {
      return new BuildOrdersCreator(project, nodes).createBuildOrderForDirtyFiles(dirtySortedErlangFiles);
    }

    private static String getParent(@NotNull String path) {
      int index = path.lastIndexOf(File.separatorChar);
      return index > 0 ? path.substring(0, index) : "";
    }

    @NotNull
    public Map<String, ErlangModuleBuildOrder> createBuildOrderForDirtyFiles(@NotNull Set<String> erlangDirtyFiles) {
      return createBuildOrderForSortedDirtyModules(getSortedDirtyModules(erlangDirtyFiles));
    }

    @NotNull
    private List<String> getSortedDirtyModules(@NotNull final Set<String> allDirtyFiles) {
      return ContainerUtil.mapNotNull(mySortedNodes, new Function<ErlangFileDescriptor, String>() {
        @Nullable
        @Override
        public String fun(ErlangFileDescriptor node) {
          return node.myPath.endsWith(".erl") && allDirtyFiles.contains(node.myPath) ? node.myPath : null;
        }
      });
    }

    @NotNull
    SourceRootInfo getSourceRootInfoForFile(@NotNull String filePath) {
      String path = filePath;
      while (!path.isEmpty() && !mySourceInfoIndex.containsKey(path)) {
        path = getParent(path);
      }
      SourceRootInfo rootInfo = mySourceInfoIndex.get(path);
      assert rootInfo != null;
      return rootInfo;
    }

    @NotNull
    private Map<String, ErlangModuleBuildOrder> createBuildOrderForSortedDirtyModules(@NotNull List<String> dirtySortedErlangModules) {
      Map<String, ErlangModuleBuildOrder> buildOrders = ContainerUtil.newHashMap();
      for (String filePath : dirtySortedErlangModules) {
        SourceRootInfo rootInfo = getSourceRootInfoForFile(filePath);
        ErlangModuleBuildOrder moduleBuildOrder = getOrCreateModuleBuildOrder(rootInfo.moduleName, buildOrders);
        List<String> erlangModules = rootInfo.isTest ? moduleBuildOrder.myOrderedErlangTestFilePaths : moduleBuildOrder.myOrderedErlangFilePaths;
        erlangModules.add(filePath);
      }
      return buildOrders;
    }
  }

  private static class SourceRootInfo {
    public final String moduleName;
    public final boolean isTest;

    private SourceRootInfo(@NotNull String moduleName, boolean isTest) {
      this.moduleName = moduleName;
      this.isTest = isTest;
    }
  }
}
