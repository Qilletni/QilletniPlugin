package dev.qilletni.intellij.packages;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Follow-up notifications for package builds. */
public final class QilletniPackageNotifications {
    private QilletniPackageNotifications() {}

    /**
     * Offers to publish a package that was just built.
     *
     * @param qll the built archive, or {@code null} if it is in the default {@code build/ql-build} location
     */
    public static void buildSucceeded(@NotNull Project project, @NotNull String projectDir, @Nullable Path qll) {
        var dir = Path.of(projectDir);
        QilletniNotifications.group()
                .createNotification(QilletniBundle.message("build.notification.success"), NotificationType.INFORMATION)
                .addAction(NotificationAction.createSimpleExpiring(QilletniBundle.message("qpm.publishLocal.action"),
                        () -> QilletniPackageCommands.publishLocal(project, dir, qll)))
                .addAction(NotificationAction.createSimpleExpiring(QilletniBundle.message("qpm.publish.action"),
                        () -> QilletniPackageCommands.publish(project, dir, qll)))
                .notify(project);
    }
}
