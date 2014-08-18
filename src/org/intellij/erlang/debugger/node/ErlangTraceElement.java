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

package org.intellij.erlang.debugger.node;

import com.ericsson.otp.erlang.OtpErlangList;
import org.intellij.erlang.psi.ErlangFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ErlangTraceElement {
  private final ErlangFile myModule;
  private final String myFunction;
  private final OtpErlangList myFunctionArgs;
  private final Collection<ErlangVariableBinding> myBindings;

  public ErlangTraceElement(@NotNull ErlangFile module,
                            @NotNull String function,
                            @NotNull OtpErlangList functionArgs,
                            @NotNull Collection<ErlangVariableBinding> bindings) {
    myModule = module;
    myFunction = function;
    myFunctionArgs = functionArgs;
    myBindings = bindings;
  }

  @NotNull
  public ErlangFile getModule() {
    return myModule;
  }

  @NotNull
  public String getFunction() {
    return myFunction;
  }

  @NotNull
  public OtpErlangList getFunctionArgs() {
    return myFunctionArgs;
  }

  @NotNull
  public Collection<ErlangVariableBinding> getBindings() {
    return myBindings;
  }
}