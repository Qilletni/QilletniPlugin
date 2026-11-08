package dev.qilletni.intellij.toolwindow;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ui.UIUtil;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import org.jetbrains.annotations.NotNull;

/** Re-reads manifests, lockfiles and installed packages. */
public final class QilletniRefreshAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        if (project == null) return;
        VirtualFileManager.getInstance().asyncRefresh(() -> QilletniProjectDependencies.getInstance(project).refresh());
        var panel = UIUtil.getParentOfType(QilletniToolWindowPanel.class, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
        if (panel != null) panel.refreshStatus();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(e.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
