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

public class QilletniCollectionExprImpl extends QilletniPsiElementBase implements QilletniCollectionExpr {

  public QilletniCollectionExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull QilletniVisitor visitor) {
    visitor.visitCollectionExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof QilletniVisitor) accept((QilletniVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public QilletniCollectionUrlOrNamePair getCollectionUrlOrNamePair() {
    return PsiTreeUtil.getChildOfType(this, QilletniCollectionUrlOrNamePair.class);
  }

  @Override
  @Nullable
  public QilletniListExpression getListExpression() {
    return PsiTreeUtil.getChildOfType(this, QilletniListExpression.class);
  }

  @Override
  @NotNull
  public QilletniOrderSettings getOrderSettings() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, QilletniOrderSettings.class));
  }

  @Override
  @Nullable
  public PsiElement getCollectionType() {
    return findChildByType(COLLECTION_TYPE);
  }

  @Override
  @Nullable
  public PsiElement getId() {
    return findChildByType(ID);
  }

  @Override
  @Nullable
  public PsiElement getLeftParen() {
    return findChildByType(LEFT_PAREN);
  }

  @Override
  @Nullable
  public PsiElement getRightParen() {
    return findChildByType(RIGHT_PAREN);
  }

}
