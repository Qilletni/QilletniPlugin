package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniLhsCore;
import dev.qilletni.intellij.psi.QilletniLhsMember;
import dev.qilletni.intellij.psi.QilletniPrimaryExpr;
import org.jetbrains.annotations.NotNull;

/** Entity properties for a member name on the left of an assignment ({@code receiver.name = ...}). */
final class QilletniLhsMemberPropertiesCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        PsiElement idLeaf = CompletionUtil.getOriginalOrSelf(parameters.getPosition());
        if (!(idLeaf.getContainingFile() instanceof QilletniFile file)) return;

        var lhsMember = PsiTreeUtil.getParentOfType(idLeaf, QilletniLhsMember.class);
        if (lhsMember == null) return;
        var lhsCore = PsiTreeUtil.findChildOfType(lhsMember, QilletniLhsCore.class);
        var primary = lhsCore != null ? PsiTreeUtil.findChildOfType(lhsCore, QilletniPrimaryExpr.class) : null;

        String receiverType = QilletniCompletionUtil.inferReceiverType(primary);
        if (receiverType != null) QilletniCompletionUtil.addEntityProperties(file, receiverType, result);
    }
}
