// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.compose.preview.util.device.parser.*;

public class DeviceSpecSizeTImpl extends ASTWrapperPsiElement implements DeviceSpecSizeT {

  public DeviceSpecSizeTImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitSizeT(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public DeviceSpecUnit getUnit() {
    return findChildByClass(DeviceSpecUnit.class);
  }

  @Override
  @NotNull
  public PsiElement getNumericT() {
    return findNotNullChildByType(NUMERIC_T);
  }

}
