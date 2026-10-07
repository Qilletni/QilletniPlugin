package dev.qilletni.intellij.packages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniProjectModel;
import dev.qilletni.intellij.library.model.QilletniLockfile;
import dev.qilletni.intellij.toolchain.QilletniInstallation;
import dev.qilletni.intellij.toolchain.QilletniToolchainLauncher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs {@code qpm} and toolchain package commands in a Run tool window console, and refreshes dependencies after
 * they finish.
 */
public final class QilletniPackageCommands {
    private static final Pattern VERIFICATION_URL = Pattern.compile("Please visit:\\s*(\\S+)");
    private static final Pattern USER_CODE = Pattern.compile("enter code:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    private QilletniPackageCommands() {}

    /** {@code qpm install}: resolves {@code qilletni_info.yml} into {@code qilletni.lock} and downloads packages. */
    public static void install(@NotNull Project project, @NotNull QilletniProjectModel model) {
        runQpm(project, model.rootDir().toNioPath(), QilletniBundle.message("qpm.install.title", model.displayName()), List.of("install"), null);
    }

    /**
     * Regenerates the lockfile. {@code qpm install} only reads {@code qilletni_info.yml} when there is no lockfile, so
     * dependencies added to the manifest need the old lockfile removed first.
     */
    public static void updateLockfile(@NotNull Project project, @NotNull QilletniProjectModel model) {
        var lockfile = model.srcRoot().findChild(QilletniLockfile.FILE_NAME);
        if (lockfile != null) {
            int answer = Messages.showOkCancelDialog(project,
                    QilletniBundle.message("qpm.updateLock.confirm"),
                    QilletniBundle.message("qpm.updateLock.title"),
                    QilletniBundle.message("qpm.updateLock.ok"), Messages.getCancelButton(), Messages.getQuestionIcon());
            if (answer != Messages.OK) return;
            try {
                WriteAction.run(() -> lockfile.delete(QilletniPackageCommands.class));
            } catch (IOException e) {
                notifyError(project, QilletniBundle.message("qpm.updateLock.failed", e.getMessage()));
                return;
            }
        }
        install(project, model);
    }

    /** {@code qpm publish-local}: installs the built .qll into {@code packages-local} for other local projects. */
    public static void publishLocal(@NotNull Project project, @NotNull Path projectDir, @Nullable Path qll) {
        runQpm(project, projectDir, QilletniBundle.message("qpm.publishLocal.title"),
                List.of("publish-local", qll != null ? qll.toString() : "."), null);
    }

    /** {@code qpm publish}: uploads the built .qll to the registry, logging in first if needed. */
    public static void publish(@NotNull Project project, @NotNull Path projectDir, @Nullable Path qll) {
        if (!isLoggedIn()) {
            int answer = Messages.showOkCancelDialog(project,
                    QilletniBundle.message("qpm.publish.loginFirst"),
                    QilletniBundle.message("qpm.publish.title"),
                    QilletniBundle.message("qpm.login.action"), Messages.getCancelButton(), Messages.getInformationIcon());
            if (answer == Messages.OK) login(project);
            return;
        }
        runQpm(project, projectDir, QilletniBundle.message("qpm.publish.title"),
                List.of("publish", qll != null ? qll.toString() : "."), null);
    }

    /** {@code qpm login}: GitHub device flow. The code is offered in a notification as soon as qpm prints it. */
    public static void login(@NotNull Project project) {
        var dir = Path.of(System.getProperty("user.home"));
        runQpm(project, dir, QilletniBundle.message("qpm.login.title"), List.of("login"), new ProcessListener() {
            private String url;

            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                if (outputType == ProcessOutputTypes.SYSTEM) return;
                var urlMatch = VERIFICATION_URL.matcher(event.getText());
                if (urlMatch.find()) url = urlMatch.group(1);
                var codeMatch = USER_CODE.matcher(event.getText());
                if (codeMatch.find() && url != null) showDeviceCode(project, url, codeMatch.group(1));
            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                // config.json has changed (or the login failed); let open UI re-read the status
                QilletniAccountsListener.fire();
            }
        });
    }

    /** {@code qilletni doc}: generates HTML documentation for the project's sources. */
    public static void generateDocs(@NotNull Project project, @NotNull QilletniProjectModel model) {
        GeneralCommandLine cmd;
        try {
            cmd = QilletniToolchainLauncher.toolchainCommandLine(project, List.of("doc", model.srcRoot().getPath()));
        } catch (ExecutionException e) {
            notifyError(project, e.getMessage());
            return;
        }
        run(project, cmd.withWorkDirectory(model.rootDir().getPath()), QilletniBundle.message("toolchain.doc.title", model.displayName()), null);
    }

    /** Whether qpm has a registry token, from {@code qpm login} or the environment. */
    public static boolean isLoggedIn() {
        if (notBlank(System.getenv("QPM_TOKEN")) || notBlank(System.getenv("GITHUB_TOKEN"))) return true;
        var config = QilletniInstallation.qpmConfigFile();
        if (!Files.isRegularFile(config)) return false;
        try {
            var json = JsonParser.parseString(Files.readString(config));
            return json instanceof JsonObject obj
                    && obj.get("github") instanceof JsonObject github
                    && github.has("token") && !github.get("token").isJsonNull()
                    && notBlank(github.get("token").getAsString());
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return false;
        }
    }

    private static void runQpm(Project project, Path workDir, String title, List<String> args, @Nullable ProcessListener listener) {
        GeneralCommandLine cmd;
        try {
            cmd = QilletniToolchainLauncher.qpmCommandLine(project, args);
        } catch (ExecutionException e) {
            notifyError(project, e.getMessage());
            return;
        }
        run(project, cmd.withWorkDirectory(workDir.toFile()), title, listener);
    }

    private static void run(Project project, GeneralCommandLine cmd, String title, @Nullable ProcessListener listener) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                var handler = new KillableColoredProcessHandler(cmd);
                if (listener != null) handler.addProcessListener(listener);
                new RunContentExecutor(project, handler)
                        .withTitle(title)
                        .withActivateToolWindow(true)
                        .withRerun(() -> run(project, cmd, title, listener))
                        .withAfterCompletion(() -> QilletniProjectDependencies.getInstance(project).refresh())
                        .run();
            } catch (ExecutionException e) {
                notifyError(project, e.getMessage());
            }
        }, project.getDisposed());
    }

    private static void showDeviceCode(Project project, String url, String code) {
        QilletniNotifications.group()
                .createNotification(QilletniBundle.message("qpm.login.code.title"), QilletniBundle.message("qpm.login.code.content", code), NotificationType.INFORMATION)
                .addAction(NotificationAction.createSimpleExpiring(QilletniBundle.message("qpm.login.code.action"), () -> {
                    CopyPasteManager.getInstance().setContents(new StringSelection(code));
                    BrowserUtil.browse(url);
                }))
                .notify(project);
    }

    static void notifyError(Project project, @Nullable String message) {
        QilletniNotifications.group()
                .createNotification(message != null ? message : QilletniBundle.message("qpm.failed"), NotificationType.ERROR)
                .addAction(QilletniNotifications.openSettingsAction(project))
                .notify(project);
    }

    private static boolean notBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }
}
