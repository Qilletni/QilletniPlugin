package dev.qilletni.intellij.packages;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.settings.QilletniConfigurable;
import org.jetbrains.annotations.NotNull;

/** The plugin's notification group and common notification actions. */
public final class QilletniNotifications {
    public static final String GROUP_ID = "Qilletni";

    private QilletniNotifications() {}

    public static @NotNull NotificationGroup group() {
        return NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID);
    }

    public static @NotNull NotificationAction openSettingsAction(@NotNull Project project) {
        return NotificationAction.createSimple(QilletniBundle.message("action.openSettings"),
                () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, QilletniConfigurable.class));
    }
}
