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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;

import java.util.Collections;
import java.util.List;

public class ErlangPrepareDependenciesTargetType extends BuildTargetType<ErlangPrepareDependenciesTarget> {
  public static final ErlangPrepareDependenciesTargetType INSTANCE = new ErlangPrepareDependenciesTargetType();
  public static final String TYPE_ID = "erlang_prepare_dependencies";

  private ErlangPrepareDependenciesTargetType() {
    super(TYPE_ID);
  }

  @NotNull
  @Override
  public List<ErlangPrepareDependenciesTarget> computeAllTargets(@NotNull JpsModel model) {
    return Collections.singletonList(new ErlangPrepareDependenciesTarget(model.getProject(), this));
  }

  @NotNull
  @Override
  public BuildTargetLoader<ErlangPrepareDependenciesTarget> createLoader(@NotNull final JpsModel model) {
    return new BuildTargetLoader<ErlangPrepareDependenciesTarget>() {
      @Nullable
      @Override
      public ErlangPrepareDependenciesTarget createTarget(@NotNull String targetId) {
        if (targetId.equals(model.getProject().getName())) {
          return new ErlangPrepareDependenciesTarget(model.getProject(), ErlangPrepareDependenciesTargetType.this);
        }
        return null;
      }
    };
  }
}
