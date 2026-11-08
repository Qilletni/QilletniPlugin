package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniEntityName;
import dev.qilletni.intellij.resolve.QilletniIndexFacade;
import org.jetbrains.annotations.NotNull;

/** Entity names visible from the file, e.g. in {@code new Foo()} or at the head of a static call {@code Foo.}. */
final class QilletniEntitiesCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        if (!(parameters.getOriginalFile() instanceof QilletniFile qf)) return;

        for (var e : QilletniIndexFacade.listEntitiesInScope(qf.getProject(), qf)) {
            var nameEl = PsiTreeUtil.findChildOfType(e, QilletniEntityName.class);
            if (nameEl == null) continue;
            result.addElement(LookupElementBuilder.create(e, nameEl.getText())
                    .withIcon(AllIcons.Nodes.Class)
                    .withInsertHandler(QilletniCompletionUtil.ENTITY_CTOR_PARENS_INSERT_HANDLER));
        }
    }
}
