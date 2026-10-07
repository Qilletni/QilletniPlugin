package dev.qilletni.intellij.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * Conservative static typing utilities for Qilletni.
 * Currently supports inferring entity type from `entity_initialize` (new <Entity>(...)).
 */
public final class QilletniTypeUtil {
    private QilletniTypeUtil() {}

    /**
     * Attempts to infer a static type name (e.g., entity name) for the given expression-like element.
     * Returns null when the type cannot be safely determined.
     */
    public static @Nullable String inferStaticType(@Nullable PsiElement exprLike) {
        if (exprLike == null) return null;
        // If we were given a primary expression, try direct cases first
        if (exprLike instanceof QilletniPrimaryExpr primary) {
            var entityInit = PsiTreeUtil.findChildOfType(primary, QilletniEntityInitialize.class);
            if (entityInit != null) {
                // entity_initialize ::= NEW ID LEFT_PAREN ...
                var id = PsiTreeUtil.findChildOfType(entityInit, PsiElement.class, false, QilletniExpr.class);
                if (id != null && id.getNode() != null && id.getNode().getElementType() == QilletniTypes.ID) {
                    return id.getText();
                }
            }
        }
        // If we were given a function_call on constructor? Not applicable for now.
        return null;
    }
}
