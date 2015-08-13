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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;

import static org.intellij.erlang.compilation.ErlangModuleTextGenerator.behaviour;
import static org.intellij.erlang.compilation.ErlangModuleTextGenerator.module;
import static org.intellij.erlang.compilation.ErlangModuleTextGenerator.pt;


public class ErlangBuildTest extends ErlangCompilationTestBase {

  public void testBuildSingleFile() throws Exception {
    addSourceFile(myModule, "module1.erl", module("module1").build());
    compileAndAssertOutput(false);
  }

  public void testBuildWithTestSource() throws Exception {
    addSourceFile(myModule, "module1.erl", module("module1").build());
    addTestFile(myModule, "test1.erl", module("test1").build());
    compileAndAssertOutput(true);
  }

  public void testBuildWithParseTransform() throws Exception {
    addSourceFile(myModule, "parse_transform1.erl", pt("parse_transform1").build());
    addSourceFile(myModule, "module1.erl", module("module1").pt("parse_transform1").build());
    compileAndAssertOutput(false);
  }

  public void testBuildWithBehaviour() throws Exception {
    ErlangModuleTextGenerator.BehaviourBuilder behaviour = behaviour("behaviour1").callback("foo", 0);
    addSourceFile(myModule, "behaviour1.erl", behaviour.build());
    addSourceFile(myModule, "module1.erl", module("module1").behaviour(behaviour).build());
    compileAndAssertOutput(false);
  }

  public void testBuildWithIncludes() throws Exception {
    addSourceFile(myModule, "header.hrl", "");
    addSourceFile(myModule, "module2.erl", module("module2").include("header.hrl").build());
    myCompilationRunner.compile();
    compileAndAssertOutput(false);
  }

  public void testBuildWithStandardLibraryInclude() throws Exception {
    addSourceFile(myModule, "module2.erl", module("module2").includeLib("eunit/include/eunit.hrl").build());
    myCompilationRunner.compile();
    compileAndAssertOutput(false);
  }

  public void testBuildWithGlobalParseTransform() throws Exception {
    addSourceFile(myModule, "module1.erl", module("module1").build());
    Module otherModule = createModuleInOwnDirectoryWithSourceAndTestRoot("other");
    addSourceFile(otherModule, "parse_transform1.erl", pt("parse_transform1").build());
    addGlobalParseTransform(myModule, Collections.singleton("parse_transform1"));
    ModuleRootModificationUtil.addDependency(myModule, otherModule);
    compileAndAssertOutput(myModule, otherModule);
  }

  public void testBuildWithParseTransformInDifferentModule() throws Exception {
    Module otherModule = createModuleInOwnDirectoryWithSourceAndTestRoot("other");
    ModuleRootModificationUtil.addDependency(myModule, otherModule);
    addSourceFile(otherModule, "parse_transform1.erl", pt("parse_transform1").build());
    addSourceFile(myModule, "module1.erl", module("module1").pt("parse_transform1").build());
    compileAndAssertOutput(myModule, otherModule);
  }

  public void testBuildWithBehaviourInDifferentModule() throws Exception {
    Module otherModule = createModuleInOwnDirectoryWithSourceAndTestRoot("other");
    ModuleRootModificationUtil.addDependency(myModule, otherModule);
    ErlangModuleTextGenerator.BehaviourBuilder behaviour = behaviour("behaviour1").callback("foo", 0);
    addSourceFile(otherModule, "behaviour1.erl", behaviour.build());
    addSourceFile(myModule, "module1.erl", module("module1").behaviour(behaviour).build());
    compileAndAssertOutput(myModule, otherModule);
  }

  public void testBuildWithIncludesFormDifferentModule() throws Exception {
    Module otherModule = createModuleInOwnDirectoryWithSourceAndTestRoot("other");
    ModuleRootModificationUtil.addDependency(myModule, otherModule);
    VirtualFile includeSourceRoot = addIncludeRoot(otherModule, "include");
    addFileToDirectory(includeSourceRoot, "header.hrl", "");
    addSourceFile(myModule, "module1.erl", module("module1").include("../other/include/header.hrl").build());
    compileAndAssertOutput(myModule, otherModule);
  }
}
