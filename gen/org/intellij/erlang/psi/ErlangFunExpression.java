// This is a generated file. Not intended for manual editing.
package org.intellij.erlang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ErlangFunExpression extends ErlangExpression {

  @Nullable
  ErlangExportFunction getExportFunction();

  @NotNull
  List<ErlangFunClause> getFunClauseList();

  @Nullable
  ErlangModuleRef getModuleRef();

  @Nullable
  PsiElement getEnd();

  @NotNull
  PsiElement getFun();

}
