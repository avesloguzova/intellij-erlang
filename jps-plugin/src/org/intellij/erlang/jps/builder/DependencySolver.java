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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by user on 7/10/15.
 */
public class DependencySolver {


  private final List<ErlangModuleDescriptor> myModuleDescriptors;
  private List<String> mySortedDirtyModules;


  DependencySolver(List<ErlangModuleDescriptor> allModuleDescriptors, List<String> dirtyModules) {
    myModuleDescriptors = allModuleDescriptors;
    mySortedDirtyModules = findSortedDependecies(dirtyModules);
  }


  public List<String> getSortedDependencies() {

    return mySortedDirtyModules;
  }

  @NotNull
  private List<String> findSortedDependecies(List<String> modules) {
    SortedModuleDependencyGraph mySemiGraph = new SortedModuleDependencyGraph();
    GraphGenerator<ErlangModuleDescriptor> myGraph = GraphGenerator.create(mySemiGraph);
    for (String moduleName : modules) {
      markDirtyModules(mySemiGraph.getDescriptor(moduleName),myGraph);
    }
    List<ErlangModuleDescriptor> result = ContainerUtil.filter(myModuleDescriptors, new Condition<ErlangModuleDescriptor>() {
      @Override
      public boolean value(ErlangModuleDescriptor descriptor) {
        return descriptor.isDirty;
      }
    });
    mySortedDirtyModules = ContainerUtil.mapNotNull(result, new Function<ErlangModuleDescriptor, String>() {
      @Override
      public String fun(ErlangModuleDescriptor descriptor) {
        return descriptor.erlangModuleName;
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

  private class SortedModuleDependencyGraph implements GraphGenerator.SemiGraph<ErlangModuleDescriptor> {

    private final Map<String, Integer> myModuleIndexMap = new LinkedHashMap<String, Integer>();

    private SortedModuleDependencyGraph() {
      buildMap();
    }

    private void buildMap() {
      int i = 0;
      for (ErlangModuleDescriptor descriptor : myModuleDescriptors) {
        myModuleIndexMap.put(descriptor.erlangModuleName, i);
        i++;
      }
    }

    @Override
    public Collection<ErlangModuleDescriptor> getNodes() {
      return myModuleDescriptors;
    }

    @Override
    public Iterator<ErlangModuleDescriptor> getIn(ErlangModuleDescriptor erlangModuleDescriptor) {
      final Iterator<String> iterator = erlangModuleDescriptor.dependencies.iterator();
      return new Iterator<ErlangModuleDescriptor>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public ErlangModuleDescriptor next() {
          return myModuleDescriptors.get(myModuleIndexMap.get(iterator.next()));
        }

        @Override
        public void remove() {
          iterator.remove();
        }
      };
    }

    public ErlangModuleDescriptor getDescriptor(String moduleName) {
      return myModuleDescriptors.get(myModuleIndexMap.get(moduleName));
    }
  }
}
