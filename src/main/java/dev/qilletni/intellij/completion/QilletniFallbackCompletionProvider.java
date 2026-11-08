package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniConstructorName;
import dev.qilletni.intellij.psi.QilletniEntityName;
import dev.qilletni.intellij.psi.QilletniFunctionName;
import dev.qilletni.intellij.psi.QilletniLhsMember;
import dev.qilletni.intellij.psi.QilletniParamName;
import dev.qilletni.intellij.psi.QilletniPostfixSuffix;
import dev.qilletni.intellij.psi.QilletniPropertyName;
import dev.qilletni.intellij.psi.QilletniVarName;
import org.jetbrains.annotations.NotNull;

/** Invoked for any ID: detects the context and offers members, or variables, functions and entities. */
final class QilletniFallbackCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final QilletniMemberPropertiesCompletionProvider memberProperties = new QilletniMemberPropertiesCompletionProvider();
    private final QilletniLhsMemberPropertiesCompletionProvider lhsMemberProperties = new QilletniLhsMemberPropertiesCompletionProvider();
    private final QilletniTopLevelFunctionsCompletionProvider topLevelFunctions = new QilletniTopLevelFunctionsCompletionProvider();
    private final QilletniEntitiesCompletionProvider entities = new QilletniEntitiesCompletionProvider();

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        PsiElement idLeaf = CompletionUtil.getOriginalOrSelf(parameters.getPosition());
        if (!(idLeaf.getContainingFile() instanceof QilletniFile file)) return;

        // Skip declaration name positions
        var parent = idLeaf.getParent();
        if (parent instanceof QilletniFunctionName
                || parent instanceof QilletniEntityName
                || parent instanceof QilletniPropertyName
                || parent instanceof QilletniConstructorName
                || parent instanceof QilletniVarName
                || parent instanceof QilletniParamName) {
            return;
        }

        // Postfix member chain: both properties and methods, since parentheses may not be typed yet
        if (PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixSuffix.class) != null) {
            memberProperties.addCompletions(parameters, context, result);
            QilletniCompletionUtil.addMethodCompletions(idLeaf, result);
            return;
        }

        // Assignment LHS member chain: properties only
        if (PsiTreeUtil.getParentOfType(idLeaf, QilletniLhsMember.class) != null) {
            lhsMemberProperties.addCompletions(parameters, context, result);
            return;
        }

        // Expression head: top-level variables, top-level functions and entities
        QilletniCompletionUtil.addTopLevelVariables(file, result);
        topLevelFunctions.addCompletions(parameters, context, result);
        entities.addCompletions(parameters, context, result);
    }
}
