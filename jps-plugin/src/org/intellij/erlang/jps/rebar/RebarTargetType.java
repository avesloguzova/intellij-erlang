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
import org.intellij.erlang.jps.model.JpsErlangModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsTypedModule;

import java.util.List;

public class RebarTargetType extends BuildTargetType<RebarTarget> {
  public static final String TYPE_ID = "Rebar_target_type";
  public static final RebarTargetType INSTANCE = new RebarTargetType();

  protected RebarTargetType() {
    super(TYPE_ID);
  }

  @NotNull
  @Override
  public List<RebarTarget> computeAllTargets(@NotNull JpsModel model) {
    List<RebarTarget> targets = ContainerUtil.newArrayList();
    for (JpsTypedModule<JpsDummyElement> module : model.getProject().getModules(JpsErlangModuleType.INSTANCE)) {
      targets.add(new RebarTarget(module));
    }
    return targets;
  }

  @NotNull
  @Override
  public BuildTargetLoader<RebarTarget> createLoader(@NotNull final JpsModel model) {
    return new BuildTargetLoader<RebarTarget>() {
      @Nullable
      @Override
      public RebarTarget createTarget(@NotNull String targetId) {
        for (JpsTypedModule<JpsDummyElement> module : model.getProject().getModules(JpsErlangModuleType.INSTANCE)) {
          if (module.getName().equals(targetId)) {
            return new RebarTarget(module);
          }
        }
        return null;
      }
    };
  }
}
