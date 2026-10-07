package dev.qilletni.intellij.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.ui.QilletniMusicPicker;
import dev.qilletni.intellij.util.QilletniMusicPsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Choose Spotify Music: replaces the music literal at the caret, or inserts one at the caret.
 */
public final class QilletniSelectSpotifyAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        var editor = e.getData(CommonDataKeys.EDITOR);
        var file = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && editor != null && file instanceof QilletniFile);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        var editor = e.getData(CommonDataKeys.EDITOR);
        var file = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || !(file instanceof QilletniFile)) return;
        var element = file.findElementAt(editor.getCaretModel().getOffset());
        var context = element != null ? QilletniMusicPsiUtil.detectContext(element).orElse(null) : null;
        QilletniMusicPicker.pick(project, editor, context);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
