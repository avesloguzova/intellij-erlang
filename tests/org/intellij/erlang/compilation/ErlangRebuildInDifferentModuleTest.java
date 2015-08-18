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

import static org.intellij.erlang.compilation.ErlangModuleTextGenerator.behaviour;
import static org.intellij.erlang.compilation.ErlangModuleTextGenerator.module;
import static org.intellij.erlang.compilation.ErlangModuleTextGenerator.pt;

public class ErlangRebuildInDifferentModuleTest extends ErlangCompilationTestBase {
  public void testRebuildWithParseTransformInDifferentModule() throws Exception {
    Module otherModule = createModuleInOwnDirectoryWithSourceAndTestRoot("other");
    ModuleRootModificationUtil.addDependency(myModule, otherModule);
    VirtualFile parseTransform = addSourceFile(otherModule, "parse_transform1.erl", pt("parse_transform1").build());
    VirtualFile sourceFile = addSourceFile(myModule, "module1.erl", module("module1").pt("parse_transform1").build());
    doTestCrossModulesDependency(otherModule, parseTransform, sourceFile);
  }

  public void testRebuildWithBehaviourInDifferentModule() throws Exception {
    Module otherModule = createModuleInOwnDirectoryWithSourceAndTestRoot("other");
    ModuleRootModificationUtil.addDependency(myModule, otherModule);
    ErlangModuleTextGenerator.BehaviourBuilder behaviour = behaviour("behaviour1").callback("foo", 0);
    VirtualFile behaviourFile = addSourceFile(otherModule, "behaviour1.erl", behaviour.build());
    VirtualFile sourceFile = addSourceFile(myModule, "module1.erl", module("module1").behaviour(behaviour).build());
    doTestCrossModulesDependency(otherModule, behaviourFile, sourceFile);
  }

  public void testRebuildWithIncludesInDifferentModule() throws Exception {
    Module otherModule = createModuleInOwnDirectoryWithSourceAndTestRoot("other");
    ModuleRootModificationUtil.addDependency(myModule, otherModule);
    VirtualFile headerFile = addSourceFile(myModule, "header.hrl", "");
    VirtualFile sourceFile = addSourceFile(myModule, "module1.erl", module("module1").include("header.hrl").build());
    doTestCrossModulesDependency(otherModule, headerFile, sourceFile);
  }

  public void doTestCrossModulesDependency(Module otherModule,
                                           VirtualFile dependency,
                                           VirtualFile sourceFile) throws Exception {
    CompilationRunner compilationRunner = new CompilationRunner(myModule, otherModule);
    try {
      compilationRunner.compile();
      assertSourcesCompiled(myModule, false);
      assertSourcesCompiled(otherModule, false);
      long sourceModificationTime = lastOutputModificationTime(myModule, sourceFile);
      compilationRunner.touch(dependency);
      compilationRunner.compile();
      assertSourcesCompiled(myModule, false);
      assertSourcesCompiled(otherModule, false);
      assertNotSame(sourceModificationTime, lastOutputModificationTime(myModule, sourceFile));
    }
    finally {
      compilationRunner.tearDown();
    }
  }

}
