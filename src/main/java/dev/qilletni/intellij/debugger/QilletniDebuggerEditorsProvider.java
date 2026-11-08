package dev.qilletni.intellij.debugger;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.LocalTimeCounter;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import dev.qilletni.intellij.QilletniFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Qilletni highlighting in the Evaluate Expression, watch and breakpoint condition editors. */
final class QilletniDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {
    @Override
    public @NotNull FileType getFileType() {
        return QilletniFileType.INSTANCE;
    }

    @Override
    protected PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
        return PsiFileFactory.getInstance(project).createFileFromText("fragment.ql", QilletniFileType.INSTANCE, text,
                LocalTimeCounter.currentTime(), isPhysical);
    }
}
