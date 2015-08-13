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

import com.intellij.openapi.vfs.VirtualFile;

import static org.intellij.erlang.compilation.ErlangModuleTextGenerator.*;

public class ErlangRebuildInSingleModuleTest extends ErlangCompilationTestBase {

  public void testRebuildWithNewFile() throws Exception {
    VirtualFile sourceFile = addSourceFile(myModule, "module1.erl", module("module1").build());
    compileAndAssertOutput(false);
    long modificationTime = lastOutputModificationTime(myModule, sourceFile);
    addSourceFile(myModule, "module2.erl", module("module2").build());
    compileAndAssertOutput(false);
    assertEquals(modificationTime, lastOutputModificationTime(myModule, sourceFile));
  }

  public void testRebuildWithoutChanges() throws Exception {
    VirtualFile sourceFile1 = addSourceFile(myModule, "module1.erl", module("module1").build());
    compileAndAssertOutput(false);
    long lastModificationTime1 = lastOutputModificationTime(myModule, sourceFile1);
    compileAndAssertOutput(false);
    assertEquals(lastModificationTime1, lastOutputModificationTime(myModule, sourceFile1));
  }

  public void testRebuildWithModuleWithoutDependencies() throws Exception {
    VirtualFile sourceFile1 = addSourceFile(myModule, "module1.erl", module("module1").build());
    VirtualFile sourceFile2 = addSourceFile(myModule, "module2.erl", module("module2").build());
    compileAndAssertOutput(false);
    long lastModificationTime1 = lastOutputModificationTime(myModule, sourceFile1);
    long lastModificationTime2 = lastOutputModificationTime(myModule, sourceFile2);
    myCompilationRunner.touch(sourceFile2);
    compileAndAssertOutput(false);
    assertEquals(lastModificationTime1, lastOutputModificationTime(myModule, sourceFile1));
    assertTrue(lastModificationTime2 != lastOutputModificationTime(myModule, sourceFile2));
  }

  public void testRebuildWithParseTransform() throws Exception {
    VirtualFile parseTransformSourceFile = addSourceFile(myModule, "parse_transform1.erl", pt("parse_transform1").build());
    VirtualFile sourceFileWithDependency = addSourceFile(myModule, "module1.erl", module("module1").pt("parse_transform1").build());
    doTestRebuildInSingleModule(parseTransformSourceFile, sourceFileWithDependency);
  }

  public void testRebuildWithBehaviour() throws Exception {
    BehaviourBuilder behaviour = behaviour("behaviour1").callback("foo", 0);
    VirtualFile behaviourSourceFile = addSourceFile(myModule, "behaviour1.erl", behaviour.build());
    VirtualFile sourceFileWithDependency = addSourceFile(myModule, "module1.erl", module("module1").behaviour(behaviour).build());
    doTestRebuildInSingleModule(behaviourSourceFile, sourceFileWithDependency);
  }

  public void testRebuildWithInclude() throws Exception {
    VirtualFile headerFile = addSourceFile(myModule, "header.hrl", "");
    VirtualFile sourceFileWithDependency = addSourceFile(myModule, "module1.erl", module("module1").include("header.hrl").build());
    doTestRebuildInSingleModule(headerFile, sourceFileWithDependency);
  }

  public void testRebuildWithIncludesDirectory() throws Exception {
    VirtualFile includeSourceRoot = addIncludeRoot(myModule, "include");
    VirtualFile headerFile = addFileToDirectory(includeSourceRoot, "header.hrl", "");
    VirtualFile sourceFileWithDependency = addSourceFile(myModule, "module1.erl", module("module1").include("header.hrl").build());
    doTestRebuildInSingleModule(headerFile, sourceFileWithDependency);
  }

  public void testRebuildWithTransitiveDependencies() throws Exception {
    VirtualFile headerFile = addSourceFile(myModule, "header.hrl", "");
    BehaviourBuilder behaviour = behaviour("behaviour1").callback("foo", 0);
    addSourceFile(myModule, "behaviour1.erl", behaviour.include("header.hrl").build());
    VirtualFile sourceFileWithDependency = addSourceFile(myModule, "module1.erl", module("module1").behaviour(behaviour).build());
    doTestRebuildInSingleModule(headerFile, sourceFileWithDependency);
  }

  private void doTestRebuildInSingleModule(VirtualFile dependency,
                                           VirtualFile sourceFileWithDependency) throws Exception {
    compileAndAssertOutput(false);
    long sourceModificationTime = lastOutputModificationTime(myModule, sourceFileWithDependency);
    myCompilationRunner.touch(dependency);
    compileAndAssertOutput(false);
    assertTrue(sourceModificationTime != lastOutputModificationTime(myModule, sourceFileWithDependency));
  }


}
