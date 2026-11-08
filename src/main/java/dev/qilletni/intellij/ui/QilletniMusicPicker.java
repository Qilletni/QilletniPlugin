package dev.qilletni.intellij.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicType;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicTypeContext;
import dev.qilletni.intellij.util.QilletniMusicPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the Spotify music picker and writes the choice into the editor. Shared by the editor action, the intention
 * and the gutter icon so they all behave the same way.
 */
public final class QilletniMusicPicker {
    private QilletniMusicPicker() {}

    /**
     * @param context the music literal to replace, or {@code null} to insert at the caret
     */
    public static void pick(@NotNull Project project, @NotNull Editor editor, @Nullable QilletniMusicPsiUtil.Context context) {
        var dialog = new SelectSpotifyDialog(project, context != null ? context.type() : MusicTypeContext.SONG);
        if (!dialog.showAndGet()) return;
        var choice = dialog.getSelection().orElse(null);
        if (choice == null) return;
        boolean includeSongKeyword = dialog.getType() == MusicType.SONG && dialog.includeKeyword();

        if (context != null && context.targetNode().isValid()) {
            QilletniMusicPsiUtil.applySelection(project, editor, context, choice, includeSongKeyword);
            return;
        }
        var text = QilletniMusicPsiUtil.buildReplacementText(dialog.getType(), choice, includeSongKeyword);
        WriteCommandAction.runWriteCommandAction(project, QilletniBundle.message("spotify.picker.insert"), null, () -> {
            int caret = editor.getCaretModel().getOffset();
            editor.getDocument().insertString(caret, text);
            editor.getCaretModel().moveToOffset(caret + text.length());
            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        });
    }
}
