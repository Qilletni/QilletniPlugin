package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniPostfixExpr;
import dev.qilletni.intellij.psi.QilletniPostfixSuffix;
import dev.qilletni.intellij.psi.QilletniPrimaryExpr;
import dev.qilletni.intellij.psi.QilletniPropertyName;
import dev.qilletni.intellij.resolve.QilletniIndexFacade;
import org.jetbrains.annotations.NotNull;

/** Entity properties for a member name in a postfix chain ({@code receiver.name}). */
final class QilletniMemberPropertiesCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        PsiElement idLeaf = CompletionUtil.getOriginalOrSelf(parameters.getPosition());
        if (!(idLeaf.getContainingFile() instanceof QilletniFile file)) return;

        // Only offer properties if there's a preceding dot or we are under a postfix suffix
        boolean inSuffix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixSuffix.class) != null;
        if (!inSuffix && !QilletniCompletionUtil.hasPrecedingDot(idLeaf)) return;

        var postfix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixExpr.class);
        if (postfix == null) return;
        var primary = PsiTreeUtil.findChildOfType(postfix, QilletniPrimaryExpr.class);

        // A type receiver (Foo.) has no properties since there are no static properties
        var receiver = QilletniCompletionUtil.inferReceiver(file, primary);
        if (receiver == null || receiver.isTypeReceiver()) return;

        var entity = QilletniIndexFacade.findEntityByTypeName(idLeaf.getProject(), file, receiver.type());
        if (entity == null) return;
        for (var p : PsiTreeUtil.findChildrenOfType(entity, QilletniPropertyName.class)) {
            result.addElement(LookupElementBuilder.create(p.getText()));
        }
    }
}
