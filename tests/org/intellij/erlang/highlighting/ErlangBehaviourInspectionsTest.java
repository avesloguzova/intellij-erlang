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

package org.intellij.erlang.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.inspection.ErlangUndefinedCallbackFunctionInspection;
import org.intellij.erlang.utils.ErlangLightPlatformCodeInsightFixtureTestCase;

import java.util.List;

public class ErlangBehaviourInspectionsTest extends ErlangLightPlatformCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    //noinspection unchecked
    myFixture.enableInspections(ErlangUndefinedCallbackFunctionInspection.class);
  }

  @Override
  protected String getTestDataPath() {
    return "testData/highlighting/behaviour/";
  }

  public void testSimple() throws Exception {
    myFixture.configureByFiles("b1.erl", "b2.erl", "test.erl");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testCallbackImplementationsAreExportedOnce() throws Exception { doImplementCallbacksFixTest("testExported.erl", "b1.erl", "b2.erl"); }
  public void testTest()                                   throws Exception { doImplementCallbacksFixTest("test-qf.erl", "b1.erl", "b2.erl"); }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }

  private void doImplementCallbacksFixTest(String ... files) throws Exception {
    myFixture.configureByFiles(files);
    applyImplementAllCallbacksFix();
    String expectedResultFile = FileUtil.getNameWithoutExtension(files[0]) + "-after.erl";
    myFixture.checkResultByFile(expectedResultFile);
  }

  private void applyImplementAllCallbacksFix() {
    List<IntentionAction> availableIntentions = myFixture.filterAvailableIntentions("Implement all callbacks");
    IntentionAction action = ContainerUtil.getFirstItem(availableIntentions);
    assertNotNull(action);
    myFixture.launchAction(action);
  }
}
