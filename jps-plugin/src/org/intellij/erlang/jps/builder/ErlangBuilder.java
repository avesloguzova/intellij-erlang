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

package org.intellij.erlang.jps.builder;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.jps.model.*;
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
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.incremental.resources.StandardResourceBuilderEnabler;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.*;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ErlangBuilder extends TargetBuilder<ErlangSourceRootDescriptor, ErlangTarget> {
  public static final String ORDERED_DIRTY_FILES_PATH_PREFIX = "erlang-builder/dirty-files-";
  public static final String NAME = "erlc";
  private static final Logger LOG = Logger.getInstance(ErlangBuilder.class);

  public ErlangBuilder() {
    super(Collections.singletonList(ErlangTargetType.INSTANCE));

    //TODO provide a way to copy erlang resources
    //disables java resource builder for erlang modules
    ResourcesBuilder.registerEnabler(new StandardResourceBuilderEnabler() {
      @Override
      public boolean isResourceProcessingEnabled(@NotNull JpsModule module) {
        return !(module.getModuleType() instanceof JpsErlangModuleType);
      }
    });
  }

  @Override
  public void build(@NotNull ErlangTarget target,
                    @NotNull DirtyFilesHolder<ErlangSourceRootDescriptor, ErlangTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {

    JpsModule module = target.getModule();
    JpsProject project = module.getProject();
    ErlangCompilerOptions compilerOptions = JpsErlangCompilerOptionsExtension.getOrCreateExtension(project).getOptions();
    if (compilerOptions.myUseRebarCompiler) return;

    LOG.info("Starting build for " + target.getPresentableName());
    File sourceOutput = getBuildOutputDirectory(module, false, context);
    File testOutput = getBuildOutputDirectory(module, true, context);

    if (holder.hasRemovedFiles()) {
      LOG.debug("Remove invalid .beam and .app files.");
      removeOutputFiles(holder.getRemovedFiles(target), sourceOutput, testOutput);
    }

    buildSources(target, context, compilerOptions, outputConsumer, sourceOutput, false);
    buildSources(target, context, compilerOptions, outputConsumer, testOutput, true);

    processAppConfigFiles(holder, outputConsumer, context, sourceOutput, testOutput);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return NAME;
  }

  private static void buildSources(@NotNull ErlangTarget target,
                                   @NotNull CompileContext context,
                                   @NotNull ErlangCompilerOptions compilerOptions,
                                   @NotNull BuildOutputConsumer outputConsumer,
                                   @NotNull File outputDir,
                                   final boolean isTests) throws IOException, ProjectBuildException {
    List<String> erlangModulePathsToCompile = getErlangModulePaths(target, context, isTests);
    if (erlangModulePathsToCompile.isEmpty()) {
      String message = isTests ? "Test sources is up to date for module" : "Sources is up to date for module";
      reportMessageForModule(context, message, target.getModule().getName());
      return;
    }
    String message = isTests ? "Compile tests for module" : "Compile source code for module";
    reportMessageForModule(context, message, target.getModule().getName());
    runErlc(target, context, compilerOptions, erlangModulePathsToCompile, outputConsumer, outputDir, isTests);
  }

  private static void reportMessageForModule(@NotNull CompileContext context,
                                             @NotNull String messagePrefix,
                                             @NotNull String moduleName) {
    String message = messagePrefix + " \"" + moduleName + "\".";
    reportMessage(context, message);
  }

  private static void reportMessage(@NotNull CompileContext context, @NotNull String message) {
    LOG.info(message);
    context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.PROGRESS, message));
    context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.INFO, message));
  }

  @NotNull
  private static File getBuildOutputDirectory(@NotNull JpsModule module,
                                              boolean forTests,
                                              @NotNull CompileContext context) throws ProjectBuildException {
    JpsJavaExtensionService instance = JpsJavaExtensionService.getInstance();
    File outputDirectory = instance.getOutputDirectory(module, forTests);
    if (outputDirectory == null) {
      String errorMessage = "No output dir for module " + module.getName();
      context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.ERROR, errorMessage));
      throw new ProjectBuildException(errorMessage);
    }
    if (!outputDirectory.exists()) {
      FileUtil.createDirectory(outputDirectory);
    }
    return outputDirectory;
  }

  private static void processAppConfigFiles(DirtyFilesHolder<ErlangSourceRootDescriptor, ErlangTarget> holder,
                                            BuildOutputConsumer outputConsumer,
                                            CompileContext context,
                                            File... outputDirectories) throws IOException {
    List<File> appConfigFiles = new DirtyFileProcessor<File, ErlangTarget>() {
      @Nullable
      @Override
      protected File getDirtyElement(@NotNull File file) throws IOException {
        return isAppConfigFileName(file.getName()) ? file : null;
      }
    }.collectDirtyElements(holder);

    for (File appConfigSrc : appConfigFiles) {
      for (File outputDir : outputDirectories) {
        File appConfigDst = new File(outputDir, getAppConfigDestinationFileName(appConfigSrc.getName()));
        FileUtil.copy(appConfigSrc, appConfigDst);
        reportMessage(context, String.format("Copy %s to %s", appConfigDst.getAbsolutePath(), outputDir.getAbsolutePath()));
        outputConsumer.registerOutputFile(appConfigDst, Collections.singletonList(appConfigSrc.getAbsolutePath()));
      }
    }
  }

  private static boolean isAppConfigFileName(String fileName) {
    return fileName.endsWith(".app") || fileName.endsWith(".app.src");
  }

  @NotNull
  private static String getAppConfigDestinationFileName(String sourceFileName) {
    return StringUtil.trimEnd(sourceFileName, ".src");
  }

  private static void runErlc(ErlangTarget target,
                              CompileContext context,
                              ErlangCompilerOptions compilerOptions,
                              List<String> erlangModulePathsToCompile,
                              BuildOutputConsumer outputConsumer,
                              File outputDirectory,
                              boolean isTest) throws ProjectBuildException, IOException {
    GeneralCommandLine commandLine = getErlcCommandLine(target, context, compilerOptions, outputDirectory, erlangModulePathsToCompile, isTest);
    Process process;
    LOG.debug("Run erlc compiler with command " + commandLine.getCommandLineString());
    try {
      process = commandLine.createProcess();
    }
    catch (ExecutionException e) {
      throw new ProjectBuildException("Failed to launch erlang compiler", e);
    }
    BaseOSProcessHandler handler = new BaseOSProcessHandler(process, commandLine.getCommandLineString(), Charset.defaultCharset());
    ProcessAdapter adapter = new ErlangCompilerProcessAdapter(context, NAME, "");
    handler.addProcessListener(adapter);
    handler.startNotify();
    handler.waitFor();
    consumeFiles(outputConsumer, getBeams(erlangModulePathsToCompile, outputDirectory));
  }

  private static GeneralCommandLine getErlcCommandLine(ErlangTarget target,
                                                       CompileContext context,
                                                       ErlangCompilerOptions compilerOptions,
                                                       File outputDirectory,
                                                       List<String> erlangModulePaths,
                                                       boolean isTest) throws ProjectBuildException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    JpsModule module = target.getModule();
    JpsSdk<JpsDummyElement> sdk = ErlangTargetBuilderUtil.getSdk(context, module);
    File executable = JpsErlangSdkType.getByteCodeCompilerExecutable(sdk.getHomePath());
    commandLine.withWorkDirectory(outputDirectory);
    commandLine.setExePath(executable.getAbsolutePath());
    addCodePath(commandLine, module, target, context);
    addParseTransforms(commandLine, module);
    addDebugInfo(commandLine, compilerOptions.myAddDebugInfoEnabled);
    addIncludePaths(commandLine, module);
    addMacroDefinitions(commandLine, isTest);
    commandLine.addParameters(erlangModulePaths);
    return commandLine;
  }

  private static void addMacroDefinitions(GeneralCommandLine commandLine, boolean isTests) {
    if (isTests) {
      commandLine.addParameters("-DTEST");
    }
  }

  private static void addDebugInfo(@NotNull GeneralCommandLine commandLine, boolean addDebugInfoEnabled) {
    if (addDebugInfoEnabled) {
      commandLine.addParameter("+debug_info");
    }
  }

  private static void addIncludePaths(@NotNull GeneralCommandLine commandLine, @Nullable JpsModule module) {
    if (module == null) return;
    for (JpsTypedModuleSourceRoot<JpsDummyElement> includeDirectory : module.getSourceRoots(ErlangIncludeSourceRootType.INSTANCE)) {
      commandLine.addParameters("-I", includeDirectory.getFile().getPath());
    }
  }

  @NotNull
  private static List<String> getErlangModulePaths(@NotNull ErlangTarget target,
                                                   @NotNull CompileContext context,
                                                   boolean isTest) {
    List<String> erlangBuilderDirtyFilesInfo = getErlangModulePathsFromConfig(target, context, isTest);
    return erlangBuilderDirtyFilesInfo != null ? erlangBuilderDirtyFilesInfo : getErlangModulePathsDefault(target, isTest);
  }

  @Nullable
  private static List<String> getErlangModulePathsFromConfig(@NotNull ErlangTarget target,
                                                             @NotNull CompileContext context,
                                                             boolean isTests) {
    String fileName = ORDERED_DIRTY_FILES_PATH_PREFIX + target.getModule().getName() + ".xml";
    ErlangModuleBuildOrder erlangModuleBuildOrder = null;
    try {
      erlangModuleBuildOrder = ErlangBuilderXmlUtil.readFromXML(fileName, context, ErlangModuleBuildOrder.class);
    }
    catch (JDOMException e) {
      LOG.error("Can't read XML from " + fileName, e);
    }
    catch (IOException e) {
      LOG.warn("I/O exceptions is occurred while reading " + fileName, e);
    }
    if (erlangModuleBuildOrder == null) return null;

    List<String> modules = erlangModuleBuildOrder.myOrderedErlangFilePaths;
    if (isTests) {
      return ContainerUtil.concat(modules, erlangModuleBuildOrder.myOrderedErlangTestFilePaths);
    }
    return modules;
  }

  @NotNull
  private static List<String> getErlangModulePathsDefault(@NotNull ErlangTarget target, boolean isTests) {
    LOG.warn("Erlang module " + target.getModule().getName() + " will be fully rebuilt.");
    CommonProcessors.CollectProcessor<File> erlFilesCollector = new CommonProcessors.CollectProcessor<File>() {
      @Override
      protected boolean accept(@NotNull File file) {
        return !file.isDirectory() && FileUtilRt.extensionEquals(file.getName(), "erl");
      }
    };
    List<JpsModuleSourceRoot> sourceRoots = ContainerUtil.newArrayList();
    JpsModule module = target.getModule();
    ContainerUtil.addAll(sourceRoots, module.getSourceRoots(JavaSourceRootType.SOURCE));
    if (isTests) {
      ContainerUtil.addAll(sourceRoots, module.getSourceRoots(JavaSourceRootType.TEST_SOURCE));
    }
    for (JpsModuleSourceRoot root : sourceRoots) {
      FileUtil.processFilesRecursively(root.getFile(), erlFilesCollector);
    }
    return ContainerUtil.map(erlFilesCollector.getResults(), new Function<File, String>() {
      @NotNull
      @Override
      public String fun(@NotNull File file) {
        return file.getAbsolutePath();
      }
    });
  }

  private static void addParseTransforms(@NotNull GeneralCommandLine commandLine,
                                         @Nullable JpsModule module) throws ProjectBuildException {
    JpsErlangModuleExtension extension = JpsErlangModuleExtension.getExtension(module);
    List<String> parseTransforms = extension != null ? extension.getParseTransforms() : Collections.<String>emptyList();
    if (parseTransforms.isEmpty()) return;
    for (String ptModule : parseTransforms) {
      commandLine.addParameter("+{parse_transform, " + ptModule + "}");
    }
  }

  private static void addCodePath(@NotNull GeneralCommandLine commandLine,
                                  @NotNull JpsModule module,
                                  @NotNull ErlangTarget target,
                                  @NotNull CompileContext context) throws ProjectBuildException {
    List<JpsModule> codePathModules = ContainerUtil.newArrayList();
    collectDependentModules(module, codePathModules, ContainerUtil.<String>newHashSet());
    addModuleToCodePath(commandLine, module, target.isTests(), context);
    for (JpsModule codePathModule : codePathModules) {
      if (codePathModule != module) {
        addModuleToCodePath(commandLine, codePathModule, false, context);
      }
    }
  }

  private static void collectDependentModules(@NotNull JpsModule module,
                                              @NotNull Collection<JpsModule> addedModules,
                                              @NotNull Set<String> addedModuleNames) {
    String moduleName = module.getName();
    if (addedModuleNames.contains(moduleName)) return;
    addedModuleNames.add(moduleName);
    addedModules.add(module);
    for (JpsDependencyElement dependency : module.getDependenciesList().getDependencies()) {
      if (!(dependency instanceof JpsModuleDependency)) continue;
      JpsModuleDependency moduleDependency = (JpsModuleDependency) dependency;
      JpsModule depModule = moduleDependency.getModule();
      if (depModule != null) {
        collectDependentModules(depModule, addedModules, addedModuleNames);
      }
    }
  }

  private static void addModuleToCodePath(@NotNull GeneralCommandLine commandLine,
                                          @NotNull JpsModule module,
                                          boolean forTests,
                                          @NotNull CompileContext context) throws ProjectBuildException {
    File outputDirectory = getBuildOutputDirectory(module, forTests, context);
    commandLine.addParameters("-pa", outputDirectory.getPath());
    for (String rootUrl : module.getContentRootsList().getUrls()) {
      try {
        String path = new URL(rootUrl).getPath();
        commandLine.addParameters("-pa", path);
      }
      catch (MalformedURLException e) {
        context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.ERROR, "Failed to find content root for module: " + module.getName()));
      }
    }
  }

  @NotNull
  private static List<File> getBeams(@NotNull Collection<String> erlPaths,
                                     @NotNull final File outputDirectory) {
    return ContainerUtil.map(erlPaths, new Function<String, File>() {
      @Override
      public File fun(String filePath) {
        String name = FileUtil.getNameWithoutExtension(new File(filePath));
        return new File(outputDirectory.getAbsolutePath() + File.separator + name + ".beam");
      }
    });
  }

  private static void consumeFiles(@NotNull BuildOutputConsumer outputConsumer,
                                   @NotNull List<File> dirtyFilePaths) throws IOException {
    for (File outputFile : dirtyFilePaths) {
      if (outputFile.exists()) {
        outputConsumer.registerOutputFile(outputFile, Collections.singletonList(outputFile.getAbsolutePath()));
      }
    }
  }

  private static void removeOutputFiles(@NotNull Collection<String> removedFiles, @NotNull File... outputDirectories) {
    for (File dir : outputDirectories) {
      List<File> outputErlangModuleFiles = ContainerUtil.concat(getBeams(findErlFiles(removedFiles), dir),
                                                                getAppsOutput(findAppFiles(removedFiles), dir));
      for (File output : outputErlangModuleFiles) {
        if (output.exists()) {
          //noinspection ResultOfMethodCallIgnored
          output.delete();
        }
      }
    }
  }

  @NotNull
  private static List<String> findAppFiles(@NotNull Collection<String> removedFiles) {
    return ContainerUtil.filter(removedFiles, new Condition<String>() {
      @Override
      public boolean value(String filePath) {
        return isAppConfigFileName(filePath);
      }
    });
  }

  private static List<File> getAppsOutput(@NotNull List<String> sourceApps, final File outputDir) {
    return ContainerUtil.map(sourceApps, new Function<String, File>() {
      @Override
      public File fun(String sourceFile) {
        return new File(outputDir, getAppConfigDestinationFileName(sourceFile));
      }
    });
  }

  @NotNull
  private static List<String> findErlFiles(@NotNull Collection<String> removedFiles) {
    return ContainerUtil.filter(removedFiles, new Condition<String>() {
      @Override
      public boolean value(String s) {
        return s.endsWith(".erl");
      }
    });
  }
}