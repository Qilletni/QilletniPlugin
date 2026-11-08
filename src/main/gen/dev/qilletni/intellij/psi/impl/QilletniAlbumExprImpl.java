// This is a generated file. Not intended for manual editing.
package dev.qilletni.intellij.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static dev.qilletni.intellij.psi.QilletniTypes.*;
import dev.qilletni.intellij.psi.*;

public class QilletniAlbumExprImpl extends QilletniPsiElementBase implements QilletniAlbumExpr {

  public QilletniAlbumExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull QilletniVisitor visitor) {
    visitor.visitAlbumExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof QilletniVisitor) accept((QilletniVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public QilletniAlbumUrlOrNamePair getAlbumUrlOrNamePair() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, QilletniAlbumUrlOrNamePair.class));
  }

  @Override
  @NotNull
  public QilletniOrderSettings getOrderSettings() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, QilletniOrderSettings.class));
  }

}
