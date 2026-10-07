package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.util.ProcessingContext;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.resolve.QilletniIndexFacade;
import org.jetbrains.annotations.NotNull;

/** Top-level functions visible from the file. */
final class QilletniTopLevelFunctionsCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        if (!(parameters.getOriginalFile() instanceof QilletniFile qf)) return;
        for (var f : QilletniIndexFacade.listTopLevelFunctions(qf.getProject(), qf)) {
            QilletniCompletionUtil.addFunction(result, f);
        }
    }
}
