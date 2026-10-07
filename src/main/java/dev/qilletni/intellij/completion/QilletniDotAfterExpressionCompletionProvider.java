package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniPostfixExpr;
import dev.qilletni.intellij.psi.QilletniPrimaryExpr;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.resolve.QilletniPsiUtil;
import org.jetbrains.annotations.NotNull;

/** Properties and methods of the receiver when the caret is at a dot or at an error element right after one. */
final class QilletniDotAfterExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        PsiElement pos = parameters.getPosition();
        if (!(parameters.getOriginalFile() instanceof QilletniFile qf)) return;

        boolean atErrorAfterDot = QilletniPsiUtil.isToken(pos, TokenType.ERROR_ELEMENT) && QilletniCompletionUtil.hasPrecedingDot(pos);
        boolean atDot = QilletniPsiUtil.isToken(pos, QilletniTypes.DOT);
        if (!atErrorAfterDot && !atDot) return;

        // The receiver is the primary on the left of the postfix chain
        var postfix = PsiTreeUtil.getParentOfType(pos, QilletniPostfixExpr.class);
        if (postfix == null) {
            // At a DOT leaf the postfix expr may only be reachable through the previous leaf
            postfix = PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(pos, true), QilletniPostfixExpr.class);
            if (postfix == null) return;
        }
        var primary = PsiTreeUtil.findChildOfType(postfix, QilletniPrimaryExpr.class);
        if (primary == null) return;

        QilletniCompletionUtil.addPropertiesForPrimary(qf, primary, result);
        QilletniCompletionUtil.addMethodsForPrimary(qf, primary, result);
    }
}
