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

package org.intellij.erlang.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.index.ErlangModuleIndex;
import org.intellij.erlang.psi.ErlangQAtom;
import org.jetbrains.annotations.NotNull;

public class ErlangModuleReferenceImpl<T extends ErlangQAtom> extends ErlangQAtomBasedReferenceImpl<T> {
  public ErlangModuleReferenceImpl(@NotNull T element) {
    super(element, ErlangPsiImplUtil.getTextRangeForReference(element), ErlangPsiImplUtil.getNameIdentifier(element).getText());
  }

  @Override
  public PsiElement resolve() {
    return ContainerUtil.getFirstItem(ErlangModuleIndex.getModulesByName(myElement.getProject(), myReferenceName, GlobalSearchScope.allScope(myElement.getProject())));
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
