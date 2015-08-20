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

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.configuration.ErlangCompilerSettings;
import org.intellij.erlang.jps.builder.ErlangPrepareDependenciesTargetType;
import org.intellij.erlang.jps.rebar.RebarBuilder;
import org.intellij.erlang.jps.rebar.RebarTargetType;
import org.intellij.erlang.module.ErlangModuleType;
import org.intellij.erlang.psi.ErlangFile;
import org.intellij.erlang.psi.ErlangTupleExpression;
import org.intellij.erlang.rebar.util.ErlangTermFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ErlangTargetScopeProvider extends BuildTargetScopeProvider {

  private static boolean hasErlangModules(@NotNull CompileScope baseScope) {
    for (Module module : baseScope.getAffectedModules()) {
      if (ModuleType.get(module) instanceof ErlangModuleType) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static List<TargetTypeBuildScope> getBuildScopesForProject(Project project, boolean forceBuild) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    List<String> targetIds = ContainerUtil.newArrayList();
    for (int i = modules.length - 1; i >= 0; i--) {
      Module module = modules[i];
      if (moduleManager.getModuleDependentModules(module).isEmpty()) {
        targetIds.add(module.getName());
      }
    }
    assert targetIds.size() > 0 : "Independent modules. ";
    return Collections.singletonList(CmdlineProtoUtil.createTargetsScope(RebarTargetType.TYPE_ID,
                                                                         targetIds,
                                                                         forceBuild));
  }

  public static List<TargetTypeBuildScope> getBuildScopesForModuleMake(Project project,
                                                                       Module[] affectedModules,
                                                                       boolean forceBuild) throws IOException {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module source = findSource(affectedModules, moduleManager);
    writeNewConfigFile(project, source);
    return Collections.singletonList(CmdlineProtoUtil.createTargetsScope(RebarTargetType.TYPE_ID, Collections.singletonList(source.getName()), forceBuild));
  }

  //  }
//
  private static PsiFile createModifiedConfigPsi(Project project, File oldConfig, String filename) throws IOException {
    String oldConfigText = oldConfig.exists() ? new String(FileUtil.loadFileText(oldConfig)) : "";
    ErlangFile configPsi = (ErlangFile) PsiFileFactory.getInstance(project)
                                                      .createFileFromText(filename, ErlangFileType.TERMS, oldConfigText);
    List<ErlangTupleExpression> depsDirSection = ErlangTermFileUtil.getConfigSections(configPsi, "deps-dir");
    if (depsDirSection.isEmpty()) {
      return configPsi;

//      configPsi.add(ErlangElementFactory.createWhitespaceFromText(project, "\n"));
//      configPsi.add(ErlangTermFileUtil.createForm(EUNIT_OPTS));
    }
    else {
//      removeReportOptions(eunitOptsSections);
//      addEunitTeamcityReportOptions(eunitOptsSections.get(0));
    }
    return configPsi;
  }
//  private void writeModifiedConfig(File oldConfig, final File newConfig) throws IOException {
//    Project project = myConfiguration.getProject();
//    final PsiFile configPsi = createModifiedConfigPsi(oldConfig);

  private static void writeNewConfigFile(Project project, Module module) throws IOException {
    File oldConfig = getOldConfig(module);
    File projectSystemDirectory = BuildManager.getInstance().getProjectSystemDirectory(project);
    File configDir = new File(projectSystemDirectory, RebarBuilder.CONFIGURATION_PATH);
    if (!configDir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      configDir.mkdir();
    }
    String configName = module.getName() + RebarBuilder.CONFIG_FILE_SUFFIX;
    final File newConfig = new File(configDir, configName);
    final PsiFile configPsi = createModifiedConfigPsi(project, oldConfig, configName);
    VirtualFile outputDirectory = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newConfig.getParentFile());
    final PsiDirectory psiDirectory = outputDirectory != null ? PsiDirectoryFactory.getInstance(project).createDirectory(outputDirectory) : null;
    if (psiDirectory == null) {
//      throw new IOException("Failed to save modified rebar.config");
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        configPsi.setName(newConfig.getName());
        psiDirectory.add(configPsi);
      }
    });


  }

  private static File getOldConfig(Module source) {
    VirtualFile moduleFile = source.getModuleFile();
    assert moduleFile != null;
    String oldParentDir = moduleFile.getParent().getPath();
    return new File(oldParentDir, "rebar.config");
  }

  private static Module findSource(Module[] modules, ModuleManager moduleManager) {
    Set<Module> moduleSet = ContainerUtil.newHashSet(Arrays.asList(modules));
    Module source = null;
    for (Module module : modules) {
      List<Module> moduleDependentModules = moduleManager.getModuleDependentModules(module);
      if (isSource(moduleSet, moduleDependentModules)) {
        source = module;
        break;
      }
    }
    assert source != null;
    return source;
  }

  private static boolean isSource(Set<Module> moduleSet, List<Module> moduleDependentModules) {
    boolean isSource = true;
    for (Module dependentModule : moduleDependentModules) {
      if (moduleSet.contains(dependentModule)) {
        isSource = false;
        break;
      }
    }
    return isSource;
  }

  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope,
                                                         @NotNull CompilerFilter filter,
                                                         @NotNull final Project project,
                                                         final boolean forceBuild) {
    if (!hasErlangModules(baseScope)) {
      return ContainerUtil.emptyList();
    }
    if (ErlangCompilerSettings.getInstance(project).isUseRebarCompilerEnabled()) {
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      final Module[] affectedModules = baseScope.getAffectedModules();
      if (affectedModules.length == moduleManager.getModules().length) {
        //All modules are affected. Rebuild whole project.
        return ApplicationManager.getApplication().runReadAction(new Computable<List<TargetTypeBuildScope>>() {
          @Override
          public List<TargetTypeBuildScope> compute() {
            return getBuildScopesForProject(project, forceBuild);
          }
        });
      }
      else {
        //Make module action
        try {
          return ApplicationManager.getApplication().runReadAction(new ThrowableComputable<List<TargetTypeBuildScope>, IOException>() {
            @Override
            public List<TargetTypeBuildScope> compute() throws IOException {
              return getBuildScopesForModuleMake(project, affectedModules, forceBuild);
            }
          });
        }
        catch (IOException e) {
          e.printStackTrace();//TODO
          return ContainerUtil.emptyList();
        }

      }
    }
    else {
      return Collections.singletonList(CmdlineProtoUtil.createTargetsScope(ErlangPrepareDependenciesTargetType.TYPE_ID,
                                                                           Collections.singletonList(project.getName()),
                                                                           forceBuild));
    }
  }
}
