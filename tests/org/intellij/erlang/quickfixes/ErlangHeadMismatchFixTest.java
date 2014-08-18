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

package org.intellij.erlang.quickfixes;

import org.intellij.erlang.inspection.ErlangHeadMismatchInspection;

public class ErlangHeadMismatchFixTest extends ErlangQuickFixTestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    //noinspection unchecked
    myFixture.enableInspections(ErlangHeadMismatchInspection.class);
  }

  @Override
  protected String getTestDataPath() {
    return "testData/quickfixes/head_mismatch/";
  }

  public void testSimple() throws Throwable { doTest("Rename clause head"); }

  public void testFun_expr_delete() throws Throwable { doFunExprTest(); }
  public void testFun_expr_insert() throws Throwable { doFunExprTest(); }
  public void testFun_expr_rename() throws Throwable { doFunExprTest(); }

  private void doFunExprTest() throws Throwable {
    doTest("Change clause name");
  }
}
