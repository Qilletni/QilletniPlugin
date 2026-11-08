package dev.qilletni.intellij.refactoring;

import com.intellij.lang.ImportOptimizer;
import com.intellij.psi.PsiFile;
import dev.qilletni.intellij.QilletniFile;
import org.jetbrains.annotations.NotNull;

public final class QilletniImportOptimizer implements ImportOptimizer {
    @Override
    public boolean supports(@NotNull PsiFile file) {
        return file instanceof QilletniFile;
    }

    @Override
    public @NotNull Runnable processFile(PsiFile file) {
        // No-op for now: scaffold only. Future iteration will sort, group, and remove unused imports.
        return () -> {};
    }
}