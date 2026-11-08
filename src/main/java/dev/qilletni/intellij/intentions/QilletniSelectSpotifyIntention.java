package dev.qilletni.intellij.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.ui.QilletniMusicPicker;
import dev.qilletni.intellij.util.QilletniMusicPsiUtil;
import org.jetbrains.annotations.NotNull;

/** Alt+Enter on a song, album or collection literal: pick a replacement from Spotify. */
public final class QilletniSelectSpotifyIntention implements IntentionAction {
    @Override
    public @NotNull String getText() {
        return QilletniBundle.message("spotify.picker.intention");
    }

    @Override
    public @NotNull String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null) return false;
        var element = file.findElementAt(editor.getCaretModel().getOffset());
        return element != null && QilletniMusicPsiUtil.detectContext(element).isPresent();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        var element = file.findElementAt(editor.getCaretModel().getOffset());
        if (element == null) return;
        QilletniMusicPsiUtil.detectContext(element).ifPresent(context -> QilletniMusicPicker.pick(project, editor, context));
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
