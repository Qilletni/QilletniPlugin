package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import dev.qilletni.intellij.QilletniFile;
import org.jetbrains.annotations.NotNull;

/**
 * Handles typed characters to trigger auto-popup completion for Qilletni files.
 * Specifically, triggers completion popup when '.' is typed for member access.
 */
public class QilletniTypedHandler extends TypedHandlerDelegate {
    @Override
    public @NotNull Result checkAutoPopup(char charTyped,
                                          @NotNull Project project,
                                          @NotNull Editor editor,
                                          @NotNull PsiFile file) {
        if (!(file instanceof QilletniFile)) {
            return Result.CONTINUE;
        }

        if (charTyped == '.') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
            return Result.STOP;
        }

        return Result.CONTINUE;
    }
}
