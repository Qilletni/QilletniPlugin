package dev.qilletni.intellij.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import dev.qilletni.intellij.packages.QilletniPackageCommands;
import dev.qilletni.intellij.settings.QilletniConfigurable;
import dev.qilletni.intellij.settings.QilletniSpotifyConfigurable;
import dev.qilletni.intellij.spotify.auth.SpotifyAuthService;
import org.jetbrains.annotations.NotNull;

/** Account and settings actions shown in Tools | Qilletni and the Qilletni tool window. */
public final class QilletniAccountActions {
    private QilletniAccountActions() {}

    /** Signs in to Spotify, or opens the settings first when no Client ID is configured. */
    public static final class SpotifySignIn extends DumbAwareAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            var auth = SpotifyAuthService.getInstance();
            if (auth.getClientId() == null) {
                ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), QilletniSpotifyConfigurable.class);
            } else {
                auth.signInWithProgress(e.getProject(), null);
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    public static final class RegistryLogin extends DumbAwareAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            if (e.getProject() != null) QilletniPackageCommands.login(e.getProject());
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(e.getProject() != null);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    public static final class OpenSettings extends DumbAwareAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), QilletniConfigurable.class);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }
}
