/*
 * Copyright 2012-2014 Sergey Ignatov
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

package org.intellij.erlang.compilation;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import org.intellij.erlang.configuration.ErlangCompilerSettings;
import org.intellij.erlang.facet.ErlangFacet;
import org.intellij.erlang.index.ErlangModuleIndex;
import org.intellij.erlang.jps.builder.ErlangFileDescriptor;
import org.intellij.erlang.jps.builder.ErlangPrepareDependenciesBuilder;
import org.intellij.erlang.jps.builder.ErlangProjectBuildOrder;
import org.intellij.erlang.psi.ErlangFile;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.intellij.erlang.utils.ErlangModulesUtil;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ErlangPrepareDependenciesCompileTask implements CompileTask {
  private static final Logger LOG = Logger.getInstance(ErlangPrepareDependenciesBuilder.class);

  @Override
  public boolean execute(final CompileContext context) {
    Project project = context.getProject();
    if (ErlangCompilerSettings.getInstance(project).isUseRebarCompilerEnabled()) {
      // delegate dependencies resolution to rebar
      return true;
    }
    LOG.info("Prepare build order for project " + project.getName());

    File projectSystemDirectory = BuildManager.getInstance().getProjectSystemDirectory(project);
    if (projectSystemDirectory == null) {
      addPrepareDependenciesFailedMessage(context);
      return true;
    }
    ErlangProjectBuildOrder projectBuildOrder = ApplicationManager.getApplication().runReadAction(new Computable<ErlangProjectBuildOrder>() {
      @Nullable
      @Override
      public ErlangProjectBuildOrder compute() {
        return getProjectBuildOrder(context);
      }
    });
    if (projectBuildOrder == null) {
      return false; // errors are reported to context.
    }
    try {
      LOG.debug("Serialize build order");
      Document serializedDocument = new Document(XmlSerializer.serialize(projectBuildOrder, new SkipDefaultValuesSerializationFilters()));
      File file = new File(projectSystemDirectory, ErlangPrepareDependenciesBuilder.BUILD_ORDER_FILE_PATH);
      //noinspection ResultOfMethodCallIgnored
      file.getParentFile().mkdirs();
      LOG.debug("Write build order to " + file.getAbsolutePath());
      JDOMUtil.writeDocument(serializedDocument, file, SystemProperties.getLineSeparator());
    }
    catch (XmlSerializationException e) {
      LOG.error("Can't serialize build order object.", e);
      addPrepareDependenciesFailedMessage(context);
      return true;
    }
    catch (IOException e) {
      LOG.warn("Some I/O errors occurred while writing build orders to file", e);
      addPrepareDependenciesFailedMessage(context);
      return true;
    }

    return true;
  }

  public static void addPrepareDependenciesFailedMessage(@NotNull CompileContext context) {
    context.addMessage(CompilerMessageCategory.WARNING, "Failed to submit dependencies info to compiler.", null, -1, -1);
  }

  @TestOnly
  @NotNull
  static List<ErlangFileDescriptor> getBuildOrder(@NotNull Module module) throws CyclicDependencyFoundException {
    return getTopologicallySortedFileDescriptors(module);
  }

  @Nullable
  private static ErlangProjectBuildOrder getProjectBuildOrder(@NotNull CompileContext context) {
    try {
      Module[] modulesToCompile = context.getCompileScope().getAffectedModules();
      return new ErlangProjectBuildOrder(getTopologicallySortedFileDescriptors(modulesToCompile));
    }
    catch (CyclicDependencyFoundException e) {
      String message = "Cyclic erlang module dependency detected. Check files " +
                       e.getFistFileInCycle() + " and " + e.getLastFileInCycle() +
                       "or their dependencies(parse_transform, behaviour, include)";
      LOG.debug(message, e);
      context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      return null;
    }
  }

  @NotNull
  private static List<String> getGlobalParseTransform(@NotNull Module module) {
    ErlangFacet erlangFacet = ErlangFacet.getFacet(module);
    return erlangFacet != null ? erlangFacet.getConfiguration().getParseTransforms() : ContainerUtil.<String>emptyList();
  }

  @NotNull
  private static List<ErlangFileDescriptor> getTopologicallySortedFileDescriptors(@NotNull Module... modulesToCompile) throws CyclicDependencyFoundException {
    final ErlangFilesDependencyGraph semiGraph = ErlangFilesDependencyGraph.createSemiGraph(modulesToCompile);
    DFSTBuilder<String> builder = new DFSTBuilder<String>(GraphGenerator.create(semiGraph));
    builder.buildDFST();
    if (!builder.isAcyclic()) {
      throw new CyclicDependencyFoundException(builder.getCircularDependency());
    }
    return ContainerUtil.map(builder.getSortedNodes(), new Function<String, ErlangFileDescriptor>() {
      @Override
      public ErlangFileDescriptor fun(String filePath) {
        return new ErlangFileDescriptor(filePath, semiGraph.getDependencies(filePath));
      }
    });
  }

  private static class ErlangFilesDependencyGraph implements GraphGenerator.SemiGraph<String> {

    private final Project myProject;
    private final PsiManager myPsiManager;
    private final Set<String> myHeaders;
    private final Map<String, List<String>> myPathsToDependenciesMap = ContainerUtil.newHashMap();

    private ErlangFilesDependencyGraph(@NotNull Module[] modulesToCompile) {
      assert modulesToCompile.length > 0;
      myProject = modulesToCompile[0].getProject();
      myPsiManager = PsiManager.getInstance(myProject);
      myHeaders = collectHeaderPaths(modulesToCompile);
      for (Module module : modulesToCompile) {
        buildDependenciesMap(module);
      }
    }

    @NotNull
    public static ErlangFilesDependencyGraph createSemiGraph(@NotNull Module[] modulesToCompile) {
      return new ErlangFilesDependencyGraph(modulesToCompile);
    }

    @NotNull
    private static Set<String> collectHeaderPaths(@NotNull Module[] modulesToCompile) {
      Set<String> erlangHeaders = ContainerUtil.newHashSet();
      for (Module module : modulesToCompile) {
        erlangHeaders.addAll(getHeaders(module, false));
        erlangHeaders.addAll(getHeaders(module, true));
      }
      return erlangHeaders;
    }

    @NotNull
    private static List<String> getHeaders(Module module, boolean onlyTestModules) {
      return ContainerUtil.map(ErlangModulesUtil.getErlangHeaderFiles(module, onlyTestModules), new Function<VirtualFile, String>() {
        @Override
        public String fun(VirtualFile virtualFile) {
          return virtualFile.getPath();
        }
      });
    }

    @Override
    public Collection<String> getNodes() {
      return myPathsToDependenciesMap.keySet();
    }

    @Override
    public Iterator<String> getIn(@NotNull String filePath) {
      return myPathsToDependenciesMap.get(filePath).iterator();
    }

    @NotNull
    public List<String> getDependencies(@NotNull String filePath) {
      List<String> dependencies = myPathsToDependenciesMap.get(filePath);
      assert dependencies != null;
      return dependencies;
    }

    private void buildDependenciesMap(@NotNull Module module) {
      List<String> globalParseTransform = getGlobalParseTransform(module);
      buildDependenciesMap(module, ErlangModulesUtil.getErlangHeaderFiles(module, false), ContainerUtil.<String>emptyList());
      buildDependenciesMap(module, ErlangModulesUtil.getErlangHeaderFiles(module, true), ContainerUtil.<String>emptyList());
      buildDependenciesMap(module, ErlangModulesUtil.getErlangModuleFiles(module, false), globalParseTransform);
      buildDependenciesMap(module, ErlangModulesUtil.getErlangModuleFiles(module, true), globalParseTransform);
    }

    private void buildDependenciesMap(@NotNull Module module, @NotNull Collection<VirtualFile> erlangModules,
                                      @NotNull List<String> globalParseTransforms) {
      for (VirtualFile erlangModule : erlangModules) {
        Set<String> dependencies = ContainerUtil.newHashSet();
        ErlangFile erlangFile = getErlangFile(erlangModule);
        addDeclaredDependencies(module, erlangFile, dependencies);
        dependencies.addAll(resolvePathsFromNames(globalParseTransforms, module));
        myPathsToDependenciesMap.put(erlangModule.getPath(), ContainerUtil.newArrayList(dependencies));
      }
    }

    @NotNull
    private ErlangFile getErlangFile(@NotNull VirtualFile virtualFile) {
      PsiFile psiFile = myPsiManager.findFile(virtualFile);
      ErlangFile erlangFile = ObjectUtils.tryCast(psiFile, ErlangFile.class);
      assert erlangFile != null;
      return erlangFile;
    }

    private void addDeclaredDependencies(@NotNull Module module,
                                         @NotNull ErlangFile erlangModule,
                                         @NotNull Set<String> dependencies) {
      dependencies.addAll(getDeclaredParseTransformPaths(module, erlangModule));
      dependencies.addAll(getDeclaredBehaviourPaths(module, erlangModule));
      dependencies.addAll(getDeclaredIncludePaths(erlangModule));
    }

    @NotNull
    private List<String> getDeclaredParseTransformPaths(@NotNull Module module, @NotNull ErlangFile erlangModule) {
      Set<String> pt = ContainerUtil.newHashSet();
      erlangModule.addDeclaredParseTransforms(pt);
      return resolvePathsFromNames(pt, module);
    }

    @NotNull
    private List<String> getDeclaredBehaviourPaths(@NotNull Module module, @NotNull ErlangFile erlangModule) {
      Set<String> behaviours = ContainerUtil.newHashSet();
      ErlangPsiImplUtil.addDeclaredBehaviourModuleNames(erlangModule, behaviours);
      return resolvePathsFromNames(behaviours, module);
    }

    @NotNull
    private List<String> resolvePathsFromNames(@NotNull Collection<String> erlangModuleNames, @NotNull Module module) {
      List<String> paths = ContainerUtil.newArrayList();
      for (String erlangModuleName : erlangModuleNames) {
        paths.addAll(getPathsFromModuleName(erlangModuleName, module));
      }
      return paths;
    }

    @NotNull
    private List<String> getDeclaredIncludePaths(@NotNull ErlangFile file) {
      return ContainerUtil.mapNotNull(ErlangPsiImplUtil.getDirectlyIncludedFiles(file), new Function<ErlangFile, String>() {
        @Nullable
        @Override
        public String fun(ErlangFile erlangFile) {
          VirtualFile virtualFile = erlangFile.getVirtualFile();
          return virtualFile != null && myHeaders.contains(virtualFile.getPath()) ? virtualFile.getPath() : null;
        }
      });
    }

    @NotNull
    private List<String> getPathsFromModuleName(@NotNull String erlangModuleName, @NotNull Module module) {
      List<ErlangFile> filesByName = ErlangModuleIndex.getFilesByName(myProject,
                                                                      erlangModuleName,
                                                                      GlobalSearchScope.moduleWithDependenciesScope(module));
      return ContainerUtil.mapNotNull(filesByName, new Function<ErlangFile, String>() {
        @Nullable
        @Override
        public String fun(ErlangFile erlangFile) {
          VirtualFile virtualFile = erlangFile.getVirtualFile();
          return virtualFile != null ? virtualFile.getPath() : null;
        }
      });
    }
  }

  static class CyclicDependencyFoundException extends Exception {
    private final Couple<String> myCyclicDependencies;

    CyclicDependencyFoundException(@NotNull Couple<String> cyclicDependencies) {
      this.myCyclicDependencies = cyclicDependencies;
    }

    @NotNull
    public String getFistFileInCycle() {
      return myCyclicDependencies.getFirst();
    }

    @NotNull
    public String getLastFileInCycle() {
      return myCyclicDependencies.getSecond();
    }
  }
}