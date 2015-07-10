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

package org.intellij.erlang.compilation;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.intellij.erlang.jps.builder.ErlangModuleDescriptor;
import org.intellij.erlang.psi.ErlangFile;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ErlangModulesSorter {
  private final Collection<ErlangFile> myModules;
  private final List<String> myGlobalParseTransforms;
  private boolean isAcyclic = true;
  private List<ErlangModuleDescriptor> sortedModules;

  public ErlangModulesSorter(Collection<ErlangFile> modules, List<String> globalParseTransforms) {

    myModules = modules;
    myGlobalParseTransforms = globalParseTransforms;
  }

  public static ErlangModuleDescriptor getModuleDescriptor(Node node) {
    ErlangModuleDescriptor result = new ErlangModuleDescriptor();
    result.erlangModuleName = getErlangFileName(node);
    result.dependencies = ContainerUtil.mapNotNull(node.getDependencies(), new Function<Node, String>() {
      @Nullable
      @Override
      public String fun(Node node) {
        return getErlangFileName(node);
      }
    });

    return result;
  }

  @Nullable
  private static String getErlangFileName(Node node) {
    VirtualFile virtualFile = node.getModuleFile().getVirtualFile();
    return virtualFile != null ? virtualFile.getPath() : null;
  }

  public List<ErlangModuleDescriptor> getSortedModules() throws CyclicDependencyFoundException {
    if (!isAcyclic) throw new CyclicDependencyFoundException();
    return sortedModules == null ? doGetSortedModules() : sortedModules;
  }

  private List<ErlangModuleDescriptor> doGetSortedModules() throws CyclicDependencyFoundException {
    GraphGenerator<Node> graph = createModulesGraph();
    DFSTBuilder<Node> builder = new DFSTBuilder<Node>(graph);
    builder.buildDFST();
    if (!builder.isAcyclic()) {
      isAcyclic = false;
      throw new CyclicDependencyFoundException();
    }
    sortedModules = ContainerUtil.mapNotNull(builder.getSortedNodes(), new Function<Node, ErlangModuleDescriptor>() {
      @Override
      public ErlangModuleDescriptor fun(Node node) {
        return getModuleDescriptor(node);
      }
    });
    return sortedModules;

  }

  private GraphGenerator<Node> createModulesGraph() {
    return GraphGenerator.create(new ErlangModulesDependencyGraph(myModules, myGlobalParseTransforms));
  }

  public static class ErlangModulesDependencyGraph implements GraphGenerator.SemiGraph<Node> {

    private final HashMap<String, Node> myNamesToNodesMap;

    public ErlangModulesDependencyGraph(Collection<ErlangFile> modules, List<String> globalParseTransforms) {
      myNamesToNodesMap = new HashMap<String, Node>(modules.size());
      for (ErlangFile moduleFile : modules) {
        Node node = new Node(moduleFile);
        myNamesToNodesMap.put(node.getModuleName(), node);
      }
      buildDependencies(globalParseTransforms);
    }

    private void buildDependencies(List<String> globalParseTransforms) {
      List<Node> globalPtNodes = getModuleNodes(globalParseTransforms);
      for (Node module : myNamesToNodesMap.values()) {
        Set<String> moduleNames = new HashSet<String>();
        moduleNames.addAll(ErlangPsiImplUtil.getAppliedParseTransformModuleNames(module.getModuleFile()));
        moduleNames.addAll(ErlangPsiImplUtil.getImplementedBehaviourModuleNames(module.getModuleFile()));
        List<Node> dependencies = getModuleNodes(moduleNames);
        module.addDependencies(dependencies);
        module.addDependencies(globalPtNodes);
      }
    }

    private List<Node> getModuleNodes(Collection<String> parseTransforms) {
      ArrayList<Node> ptNodes = new ArrayList<Node>(parseTransforms.size());
      for (String pt : parseTransforms) {
        ContainerUtil.addIfNotNull(myNamesToNodesMap.get(pt), ptNodes);
      }
      return ptNodes;
    }

    @Override
    public Collection<Node> getNodes() {
      return myNamesToNodesMap.values();
    }

    @Override
    public Iterator<Node> getIn(Node node) {
      return node.getDependencies().iterator();
    }


  }

  public static class Node {
    private final ErlangFile myModuleFile;
    private final ArrayList<Node> myDependencies = new ArrayList<Node>();

    Node(ErlangFile moduleFile) {
      myModuleFile = moduleFile;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Node node = (Node) o;

      if (!myModuleFile.getName().equals(node.myModuleFile.getName())) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myModuleFile.getName().hashCode();
    }

    ErlangFile getModuleFile() {
      return myModuleFile;
    }

    String getModuleName() {
      return FileUtil.getNameWithoutExtension(myModuleFile.getName());
    }

    void addDependency(@Nullable Node dep) {
      if (dep != null && dep != this) {
        myDependencies.add(dep);
      }
    }

    void addDependencies(Collection<Node> deps) {
      for (Node dep : deps) {
        addDependency(dep);
      }
    }

    List<Node> getDependencies() {
      return myDependencies;
    }


  }

  public static class CyclicDependencyFoundException extends Exception {
    CyclicDependencyFoundException() {
    }
  }
}
