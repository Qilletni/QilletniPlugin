package dev.qilletni.intellij.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import dev.qilletni.intellij.library.QilletniLibraryManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Clears the extracted Qilletni libraries cache (qilletni-libs) and triggers a rescan.
 * Useful for troubleshooting when native.jar or sources appear stale.
 *
 * TODO: Remove this, this is for debugging
 */
public final class QilletniResetLibraryCacheAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        var systemPath = PathManager.getSystemPath();
        Path cacheRoot = Path.of(systemPath, "qilletni-libs");

        try {
            if (Files.exists(cacheRoot)) {
                try (var walk = Files.walk(cacheRoot)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
                }
            }
            Notifications.Bus.notify(new Notification("Qilletni Libraries", "Qilletni Libraries",
                    "Cleared Qilletni library cache. Refreshing and rescanning…", NotificationType.INFORMATION), project);
            VirtualFileManager.getInstance().asyncRefresh(() -> QilletniLibraryManager.getInstance().rescan());
        } catch (Exception ex) {
            Notifications.Bus.notify(new Notification("Qilletni Libraries", "Qilletni Libraries",
                    "Failed to clear Qilletni library cache: " + ex.getMessage(), NotificationType.ERROR), project);
        }
    }
}
