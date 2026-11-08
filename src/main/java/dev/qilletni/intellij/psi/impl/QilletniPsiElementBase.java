package dev.qilletni.intellij.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * Common base class for all Qilletni PSI element implementations.
 * Provides a Visitor-typed accept method so generated classes can @Override it safely.
 */
public class QilletniPsiElementBase extends ASTWrapperPsiElement {
    public QilletniPsiElementBase(@NotNull ASTNode node) {
        super(node);
    }
}
