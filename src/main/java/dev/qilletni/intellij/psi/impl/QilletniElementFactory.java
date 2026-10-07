package dev.qilletni.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import dev.qilletni.intellij.QilletniFileType;
import dev.qilletni.intellij.psi.QilletniTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Creates Qilletni PSI by parsing a dummy file, so new nodes get the same element and leaf types as parsed code.
 */
public final class QilletniElementFactory {
    private QilletniElementFactory() {}

    /** An identifier leaf (a {@code QilletniIdentifierLeaf}), for replacing a name during rename. */
    public static @NotNull PsiElement createIdentifier(@NotNull Project project, @NotNull String name) {
        var file = PsiFileFactory.getInstance(project).createFileFromText("dummy.ql", QilletniFileType.INSTANCE, name);
        var leaf = PsiTreeUtil.getDeepestFirst(file);
        if (leaf.getNode() == null || leaf.getNode().getElementType() != QilletniTypes.ID || !leaf.getText().equals(name)) {
            throw new IncorrectOperationException("'" + name + "' is not a valid Qilletni identifier");
        }
        return leaf;
    }

    /** Replaces the identifier leaf {@code id} with one named {@code newName} and returns the new leaf. */
    public static @NotNull PsiElement renameIdentifier(@NotNull PsiElement id, @NotNull String newName) {
        return id.replace(createIdentifier(id.getProject(), newName));
    }
}
