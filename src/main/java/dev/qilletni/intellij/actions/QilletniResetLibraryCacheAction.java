package dev.qilletni.intellij.actions;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import dev.qilletni.intellij.library.QilletniLibraryManager;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Deletes the extracted package cache and re-resolves dependencies. Useful when an installed package was replaced
 * in place and its sources or native.jar look stale.
 */
public final class QilletniResetLibraryCacheAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        new Task.Backgroundable(project, "Clearing Qilletni library cache", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    QilletniLibraryManager.getInstance().clearCache();
                    QilletniProjectDependencies.refreshAll();
                } catch (IOException ex) {
                    NotificationGroupManager.getInstance().getNotificationGroup("Qilletni")
                            .createNotification("Failed to clear the Qilletni library cache: " + ex.getMessage(), NotificationType.ERROR)
                            .notify(project);
                }
            }
        }.queue();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
