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

package org.intellij.erlang.runconfig;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.console.ErlangConsoleUtil;
import org.intellij.erlang.jps.model.JpsErlangSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ErlangRunningState extends CommandLineState {
  private final Module myModule;

  public ErlangRunningState(ExecutionEnvironment env, Module module) {
    super(env);
    myModule = module;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    GeneralCommandLine commandLine = getCommand();
    return new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
  }

  private GeneralCommandLine getCommand() throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    setExePath(commandLine);
    setWorkDirectory(commandLine);
    setCodePath(commandLine);
    setEntryPoint(commandLine);
    setStopErlang(commandLine);
    setNoShellMode(commandLine);
    setErlangFlags(commandLine);
    TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(myModule.getProject());
    setConsoleBuilder(consoleBuilder);
    return commandLine;
  }

  public List<String> getCodePath() throws ExecutionException {
    return ErlangConsoleUtil.getCodePath(myModule, useTestCodePath());
  }

  public Module getModule() {
    return myModule;
  }

  protected abstract boolean useTestCodePath();

  protected abstract boolean isNoShellMode();

  protected abstract boolean isStopErlang();

  protected List<String> getErlFlags() {
    return ContainerUtil.emptyList();
  }

  public abstract ErlangEntryPoint getEntryPoint() throws ExecutionException;

  public abstract String getWorkDirectory();

  public ErlangEntryPoint getDebugEntryPoint() throws ExecutionException {
    return getEntryPoint();
  }

  public final void setCodePath(GeneralCommandLine commandLine) throws ExecutionException {
    commandLine.addParameters(getCodePath());
  }

  public final void setExePath(GeneralCommandLine commandLine) throws ExecutionException {
    Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
    String homePath = sdk != null ? sdk.getHomePath() : null;
    if (homePath == null) {
      throw new ExecutionException("Invalid module SDK.");
    }
    commandLine.setExePath(JpsErlangSdkType.getByteCodeInterpreterExecutable(homePath).getAbsolutePath());
  }

  public final void setWorkDirectory(GeneralCommandLine commandLine) {
    String workDirectory = getWorkDirectory();
    if (workDirectory == null || workDirectory.isEmpty() ) {
      commandLine.withWorkDirectory(myModule.getProject().getBasePath());
    }else{
      commandLine.withWorkDirectory(workDirectory);
    }

  }

  public final void setStopErlang(GeneralCommandLine commandLine) {
    if (isStopErlang()) {
      commandLine.addParameters("-s", "init", "stop");
    }
  }

  public final void setNoShellMode(GeneralCommandLine commandLine) {
    if (isNoShellMode()) commandLine.addParameters("-noshell");
  }

  public final void setEntryPoint(GeneralCommandLine commandLine) throws ExecutionException {
    ErlangEntryPoint entryPoint = getEntryPoint();
    commandLine.addParameters("-eval",
      entryPoint.getModuleName() + ":" + entryPoint.getFunctionName() +
        "(" + StringUtil.join(entryPoint.getArgsList(), ", ") + ").");
  }

  public final void setErlangFlags(GeneralCommandLine commandLine) {
    commandLine.addParameters(getErlFlags());
  }

  @NotNull
  public abstract ConsoleView createConsoleView(Executor executor);

  public static class ErlangEntryPoint {
    private final String myModuleName;
    private final String myFunctionName;
    private final List<String> myArgsList;

    public ErlangEntryPoint(String moduleName, String functionName, List<String> args) {
      myModuleName = moduleName;
      myFunctionName = functionName;
      myArgsList = args;
    }

    public String getModuleName() {
      return myModuleName;
    }

    public String getFunctionName() {
      return myFunctionName;
    }

    public List<String> getArgsList() {
      return myArgsList;
    }

    @Nullable
    public static ErlangEntryPoint fromModuleAndFunction(@NotNull String moduleAndFunction, @NotNull String params) {
      List<String> split = StringUtil.split(moduleAndFunction, " ");
      if (split.size() != 2) return null;
      String module = split.get(0);
      String function = split.get(1);
      List<String> args = StringUtil.split(params, " ");
      return new ErlangEntryPoint(module, function, args);
    }
  }
}
