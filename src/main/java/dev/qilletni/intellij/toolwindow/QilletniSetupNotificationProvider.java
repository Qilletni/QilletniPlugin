package dev.qilletni.intellij.toolwindow;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.QilletniFileType;
import dev.qilletni.intellij.library.QilletniDependency;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniProjectModel;
import dev.qilletni.intellij.library.model.QilletniManifest;
import dev.qilletni.intellij.packages.QilletniPackageCommands;
import dev.qilletni.intellij.settings.QilletniConfigurable;
import dev.qilletni.intellij.toolchain.QilletniInstallation;
import dev.qilletni.intellij.toolchain.QilletniToolchainLauncher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.function.Function;

/**
 * Banner above Qilletni sources and {@code qilletni_info.yml} when something blocks running the project: the
 * toolchain is missing, or dependencies aren't installed. Each banner offers the fix.
 */
public final class QilletniSetupNotificationProvider implements EditorNotificationProvider, DumbAware {

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {
        boolean isSource = file.getFileType() == QilletniFileType.INSTANCE;
        boolean isManifest = file.getName().equals(QilletniManifest.FILE_NAME);
        if (!isSource && !isManifest) return null;

        var model = QilletniProjectDependencies.getInstance(project).findProject(file);
        // Library sources opened from External Libraries aren't the user's project; don't nag there
        if (model == null && isSource && !file.isInLocalFileSystem()) return null;

        var toolchainProblem = QilletniToolchainLauncher.findProblem(project);
        if (toolchainProblem != null) {
            boolean notInstalled = QilletniInstallation.toolchainJar() == null;
            return editor -> {
                var panel = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning);
                panel.setText(toolchainProblem);
                if (notInstalled) {
                    panel.createActionLabel(QilletniBundle.message("settings.qilletni.install"), () -> BrowserUtil.browse("https://qilletni.dev/"));
                }
                panel.createActionLabel(QilletniBundle.message("action.configure"),
                        () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, QilletniConfigurable.class));
                return panel;
            };
        }

        if (model == null || !model.status().needsInstall()) return null;
        var status = model.status();
        return editor -> {
            var panel = new EditorNotificationPanel(editor, status == QilletniProjectModel.Status.NO_LOCKFILE
                    ? EditorNotificationPanel.Status.Info : EditorNotificationPanel.Status.Warning);
            panel.setText(switch (status) {
                case MISSING_PACKAGES -> QilletniBundle.message("banner.missing",
                        String.join(", ", model.missing().stream().map(QilletniDependency::id).toList()));
                case LOCK_STALE -> QilletniBundle.message("banner.lockStale");
                default -> QilletniBundle.message("banner.noLockfile");
            });
            if (status == QilletniProjectModel.Status.LOCK_STALE) {
                panel.createActionLabel(QilletniBundle.message("qpm.updateLock.ok"), () -> QilletniPackageCommands.updateLockfile(project, model));
            } else {
                panel.createActionLabel(QilletniBundle.message("qpm.install.action"), () -> QilletniPackageCommands.install(project, model));
            }
            return panel;
        };
    }
}
