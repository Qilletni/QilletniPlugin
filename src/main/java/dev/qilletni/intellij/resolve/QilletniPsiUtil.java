package dev.qilletni.intellij.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniFunctionOnType;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.psi.QilletniVarDeclaration;
import dev.qilletni.intellij.psi.QilletniVarName;
import dev.qilletni.intellij.util.QilletniTokenSets;
import org.jetbrains.annotations.Nullable;

/**
 * Small PSI helpers shared by resolution, completion, indexing and editor features.
 */
public final class QilletniPsiUtil {
    private QilletniPsiUtil() {}

    public static boolean isToken(@Nullable PsiElement element, IElementType type) {
        return element != null && element.getNode() != null && element.getNode().getElementType() == type;
    }

    private static boolean isToken(@Nullable PsiElement element, TokenSet types) {
        return element != null && element.getNode() != null && types.contains(element.getNode().getElementType());
    }

    /** The first direct child of {@code owner} that is an {@code ID} token. */
    public static @Nullable PsiElement findDirectIdChild(@Nullable PsiElement owner) {
        if (owner == null) return null;
        for (PsiElement c = owner.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (isToken(c, QilletniTypes.ID)) return c;
        }
        return null;
    }

    public static boolean isStaticFunctionDef(QilletniFunctionDef def) {
        return def.getStatic() != null;
    }

    /** Whether the element is outside every function and entity, i.e. in the file's root scope. */
    public static boolean isTopLevel(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, QilletniFunctionDef.class, false) == null
                && PsiTreeUtil.getParentOfType(element, QilletniEntityDef.class, false) == null;
    }

    /** Text of the first type token among the direct children of {@code owner} that precede {@code stopAt}. */
    public static @Nullable String declaredTypeText(PsiElement owner, @Nullable PsiElement stopAt) {
        for (PsiElement c = owner.getFirstChild(); c != null && c != stopAt; c = c.getNextSibling()) {
            if (isToken(c, QilletniTokenSets.TYPE_TOKENS)) return c.getText();
        }
        return null;
    }

    /** The declared type of the {@code var_declaration} that owns the given name. */
    public static @Nullable String getVarDeclarationType(@Nullable QilletniVarName varName) {
        if (varName == null) return null;
        var decl = PsiTreeUtil.getParentOfType(varName, QilletniVarDeclaration.class);
        return decl != null ? declaredTypeText(decl, varName) : null;
    }

    /** The receiver type token of an extension function's {@code on <type>} clause. */
    public static @Nullable PsiElement receiverTypeToken(@Nullable QilletniFunctionOnType onType) {
        if (onType == null) return null;
        PsiElement result = null;
        for (PsiElement c = onType.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (isToken(c, QilletniTokenSets.TYPE_TOKENS)) result = c;
        }
        return result;
    }

    /** The closest preceding leaf that is not whitespace or a comment. */
    public static @Nullable PsiElement prevSignificantLeaf(@Nullable PsiElement leaf) {
        if (leaf == null) return null;
        PsiElement prev = PsiTreeUtil.prevLeaf(leaf, true);
        while (prev != null && isToken(prev, QilletniTokenSets.TRIVIA)) {
            prev = PsiTreeUtil.prevLeaf(prev, true);
        }
        return prev;
    }
}
