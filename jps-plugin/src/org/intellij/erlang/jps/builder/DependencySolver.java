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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DependencySolver {


  private final List<ErlangModuleDescriptor> myModuleDescriptors;
  private List<ErlangModuleDescriptor> mySortedDirtyModules;


  DependencySolver(List<ErlangModuleDescriptor> allModuleDescriptors, List<String> dirtyModules) {
    myModuleDescriptors = allModuleDescriptors;
    mySortedDirtyModules = findSortedDependecies(dirtyModules);
  }


  public List<String> getSortedDependencies() {

    return ContainerUtil.mapNotNull(mySortedDirtyModules, new Function<ErlangModuleDescriptor, String>() {
      @Override
      public String fun(ErlangModuleDescriptor descriptor) {
        return descriptor.erlangModuleName;
      }
    });
  }

  public List<List<String>> getSortedConnectedComponents() {
    UndirectedDependencyGraph semiGraph = new UndirectedDependencyGraph(mySortedDirtyModules);
    GraphGenerator<ErlangModuleWithUndirectedDependencies> graph = GraphGenerator.create(semiGraph);
    final DFSTBuilder<ErlangModuleWithUndirectedDependencies> builder = new DFSTBuilder<ErlangModuleWithUndirectedDependencies>(graph);
    builder.getSCCs().forEach(new TIntProcedure() {
      private int myTNumber;

      @Override
      public boolean execute(int size) {
        for (int j = 0; j < size; j++) {
          builder.getNodeByTNumber(myTNumber + j).setSCC(myTNumber);
        }
        myTNumber += size;
        return true;
      }
    });
    Map<Integer,List<String>> result = ContainerUtil.newHashMap();
    Factory<List<String>> factory = new Factory<List<String>>() {
      @Override
      public List<String> create() {
        return ContainerUtil.newArrayList();
      }
    };
    for (ErlangModuleWithUndirectedDependencies module:semiGraph.getNodes()){
      List<String> connectedComponents = ContainerUtil.getOrCreate(result, module.getSCC(), factory);
      connectedComponents.add(module.erlangModuleName);
    }
    return ContainerUtil.newArrayList(result.values());
  }

  @NotNull
  private List<ErlangModuleDescriptor> findSortedDependecies(List<String> modules) {
    SortedModuleDependencyGraph<ErlangModuleDescriptor> mySemiGraph = new SortedModuleDependencyGraph<ErlangModuleDescriptor>(myModuleDescriptors);
    GraphGenerator<ErlangModuleDescriptor> myGraph = GraphGenerator.create(mySemiGraph);
    for (String moduleName : modules) {
      markDirtyModules(mySemiGraph.getDescriptor(moduleName),myGraph);
    }
    mySortedDirtyModules = ContainerUtil.filter(myModuleDescriptors, new Condition<ErlangModuleDescriptor>() {
      @Override
      public boolean value(ErlangModuleDescriptor descriptor) {
        return descriptor.isDirty;
      }
    });

    return mySortedDirtyModules;
  }

  private static void markDirtyModules(ErlangModuleDescriptor descriptor,
                                       GraphGenerator<ErlangModuleDescriptor> myGraph) {
    descriptor.isDirty = true;
    Iterator<ErlangModuleDescriptor> childsIter = myGraph.getOut(descriptor);
    while (childsIter.hasNext()) {
      markDirtyModules(childsIter.next(), myGraph);
    }
  }

  private static class SortedModuleDependencyGraph<T extends ErlangModuleDescriptor> implements GraphGenerator.SemiGraph<T> {

    private final Map<String, T> myModuleIndexMap = new LinkedHashMap<String, T>();
    private final List<T> myModuleDescriptors;

    private SortedModuleDependencyGraph(List<T> moduleDescriptors) {
      myModuleDescriptors = moduleDescriptors;
      buildMap();
    }

    private void buildMap() {
      for (T descriptor : myModuleDescriptors) {
        myModuleIndexMap.put(descriptor.erlangModuleName, descriptor);
      }
    }

    @Override
    public Collection<T> getNodes() {
      return myModuleDescriptors;
    }

    @Override
    public Iterator<T> getIn(T erlangModuleDescriptor) {
      final Iterator<String> iterator = erlangModuleDescriptor.dependencies.iterator();
      return new Iterator<T>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public T next() {
          String string = iterator.next();
          return getDescriptor(string);
        }

        @Override
        public void remove() {
          iterator.remove();
        }
      };
    }
    public boolean contains(String moduleName){
      return myModuleIndexMap.containsKey(moduleName);
    }

    public T getDescriptor(String moduleName) {
      return myModuleIndexMap.get(moduleName);
    }
  }

  private static class ErlangModuleWithUndirectedDependencies extends ErlangModuleDescriptor {
    public ErlangModuleWithUndirectedDependencies(ErlangModuleDescriptor descriptor) {
      this.erlangModuleName = descriptor.erlangModuleName;
      this.dependencies = descriptor.dependencies;
    }

    private List<ErlangModuleWithUndirectedDependencies> fakeDependensies = ContainerUtil.newArrayList();
    private int mySCC;

    private void setSCC(int number) {

      mySCC = number;
    }

    public int getSCC() {
      return mySCC;
    }
  }

  private static class UndirectedDependencyGraph implements GraphGenerator.SemiGraph<ErlangModuleWithUndirectedDependencies> {

    SortedModuleDependencyGraph<ErlangModuleWithUndirectedDependencies> mySemiGraph;
    List<ErlangModuleWithUndirectedDependencies> myErlangModules;

    public UndirectedDependencyGraph(List<ErlangModuleDescriptor> moduleDescriptors) {
      myErlangModules = ContainerUtil.mapNotNull(moduleDescriptors, new Function<ErlangModuleDescriptor, ErlangModuleWithUndirectedDependencies>() {
        @Override
        public ErlangModuleWithUndirectedDependencies fun(ErlangModuleDescriptor descriptor) {
          return new ErlangModuleWithUndirectedDependencies(descriptor);
        }
      });
      mySemiGraph = new SortedModuleDependencyGraph<ErlangModuleWithUndirectedDependencies>(myErlangModules);
      for(ErlangModuleWithUndirectedDependencies module: mySemiGraph.getNodes()){
        Iterator<String> iterator = module.dependencies.iterator();
        while (iterator.hasNext()){
          String dependency = iterator.next();
          if(mySemiGraph.contains(dependency)){
            mySemiGraph.getDescriptor(dependency).fakeDependensies.add(module);
          }else{
            iterator.remove();
          }
        }
      }
    }

    @Override
    public Collection<ErlangModuleWithUndirectedDependencies> getNodes() {
      return mySemiGraph.getNodes();
    }

    @Override
    public Iterator<ErlangModuleWithUndirectedDependencies> getIn(final ErlangModuleWithUndirectedDependencies module) {

      return new Iterator<ErlangModuleWithUndirectedDependencies>() {

        final Iterator<ErlangModuleWithUndirectedDependencies> myRealDependenciesIterator = mySemiGraph.getIn(module);
        final Iterator<ErlangModuleWithUndirectedDependencies> myFakeDependenciesIterator = module.fakeDependensies.iterator();

        @Override
        public boolean hasNext() {
          return myRealDependenciesIterator.hasNext() || myFakeDependenciesIterator.hasNext();
        }

        @Override
        public ErlangModuleWithUndirectedDependencies next() {
          return myRealDependenciesIterator.hasNext()? myRealDependenciesIterator.next():myFakeDependenciesIterator.next();
        }

        @Override
        public void remove() {
          //TODO
        }
      };
    }
  }
}
