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

import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ErlangPrepareDependenciesTarget extends BuildTarget<ErlangSourceRootDescriptor> {
  private static final String NAME = "prepare dependencies target";

  private final JpsProject myProject;

  public ErlangPrepareDependenciesTarget(@NotNull JpsProject project,
                                         @NotNull ErlangPrepareDependenciesTargetType targetType) {
    super(targetType);
    myProject = project;
  }


  @Override
  public String getId() {
    return myProject.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry,
                                                        TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<ErlangSourceRootDescriptor> computeRootDescriptors(@NotNull JpsModel model,
                                                                 ModuleExcludeIndex index,
                                                                 IgnoredFileIndex ignoredFileIndex,
                                                                 @NotNull BuildDataPaths dataPaths) {
    List<ErlangSourceRootDescriptor> result = ContainerUtil.newArrayList();
    for (JpsModule module : model.getProject().getModules()) {
      ErlangTargetBuilderUtil.addRootDescriptors(this, module, result);
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ErlangPrepareDependenciesTarget)) return false;

    ErlangPrepareDependenciesTarget that = (ErlangPrepareDependenciesTarget) o;

    if (!myProject.equals(that.myProject)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myProject.hashCode();
  }

  @Nullable
  @Override
  public ErlangSourceRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    return ErlangTargetBuilderUtil.findRootDescriptor(rootId,
                                                      rootIndex,
                                                      (ErlangPrepareDependenciesTargetType) getTargetType());
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return NAME;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(@NotNull CompileContext context) {
    return Collections.emptyList();
  }

  @NotNull
  public JpsProject getProject() {
    return myProject;
  }


}
