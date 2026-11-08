package dev.qilletni.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import dev.qilletni.intellij.psi.QilletniTypes;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities used by Grammar-Kit generated PSI for named elements.
 * Provides name access and mutation targeting the immediate ID leaf child.
 */
public class QilletniPsiImplUtil {
    public static @Nullable String getName(PsiElement e) {
        var id = findIdNode(e);
        return id != null ? id.getText() : null;
    }

    public static @Nullable PsiElement getNameIdentifier(PsiElement e) {
        var id = findIdNode(e);
        return id != null ? id.getPsi() : null;
    }

    public static PsiElement setName(PsiElement e, String name) {
        var id = findIdNode(e);
        if (id == null) return e;
        QilletniElementFactory.renameIdentifier(id.getPsi(), name);
        return e;
    }

    private static ASTNode findIdNode(PsiElement e) {
        return e.getNode().findChildByType(QilletniTypes.ID);
    }
}
