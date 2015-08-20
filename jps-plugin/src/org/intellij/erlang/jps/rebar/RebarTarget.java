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

package org.intellij.erlang.jps.rebar;

import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.jps.builder.ErlangSourceRootDescriptor;
import org.intellij.erlang.jps.model.ErlangIncludeSourceRootType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import java.io.File;
import java.util.*;

public class RebarTarget extends BuildTarget<ErlangSourceRootDescriptor> {
  private static final String NAME = "Rebar build target";

  private final JpsModule myModule;

  protected RebarTarget(JpsModule module) {
    super(RebarTargetType.INSTANCE);
    myModule = module;
  }

  private static File getOutputRoot(JpsModule module) {
    String contentRoot = ContainerUtil.getFirstItem(module.getContentRootsList().getUrls());//TODO Check is there single content root
    return new File(contentRoot, "ebin");
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry,
                                                        TargetOutputIndex outputIndex) {
    return ContainerUtil.emptyList();
  }

  @NotNull
  @Override
  public List<ErlangSourceRootDescriptor> computeRootDescriptors(JpsModel model,
                                                                 ModuleExcludeIndex index,
                                                                 IgnoredFileIndex ignoredFileIndex,
                                                                 BuildDataPaths dataPaths) {
    List<ErlangSourceRootDescriptor> result = new ArrayList<ErlangSourceRootDescriptor>();
    for (JpsModule module : model.getProject().getModules()) {
      for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> root : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
        result.add(new ErlangSourceRootDescriptor(root.getFile(), this, false));
      }
      for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> root : module.getSourceRoots(JavaSourceRootType.TEST_SOURCE)) {
        result.add(new ErlangSourceRootDescriptor(root.getFile(), this, true));
      }
      for (JpsTypedModuleSourceRoot<JpsDummyElement> root : module.getSourceRoots(ErlangIncludeSourceRootType.INSTANCE)) {
        result.add(new ErlangSourceRootDescriptor(root.getFile(), this, false));
      }
    }
    return result;
  }

  @Nullable
  @Override
  public ErlangSourceRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    return ContainerUtil.getFirstItem(rootIndex.getRootDescriptors(new File(rootId), Collections.singletonList(RebarTargetType.INSTANCE), null));

  }

  @NotNull
  @Override
  public String getPresentableName() {
    return NAME;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    List<File> outputRoots = ContainerUtil.newArrayList();
    Set<JpsModule> modules = JpsJavaExtensionService.dependencies(myModule).includedIn(JpsJavaClasspathKind.compile(false)).getModules();//TODO what about build for tests?
    for (JpsModule module : modules) {
      outputRoots.add(getOutputRoot(module));
    }
    return outputRoots;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RebarTarget)) return false;

    RebarTarget that = (RebarTarget) o;

    if (!myModule.equals(that.myModule)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }

  public JpsModule getModule() {
    return myModule;
  }
}
