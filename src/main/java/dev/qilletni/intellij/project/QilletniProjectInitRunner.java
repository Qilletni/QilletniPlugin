package dev.qilletni.intellij.project;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.toolchain.QilletniInstallation;
import dev.qilletni.intellij.toolchain.QilletniToolchainLauncher;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Runs {@code qilletni init} and captures its output. */
public final class QilletniProjectInitRunner {
    private QilletniProjectInitRunner() {}

    public record Result(boolean success, int exitCode, String output) {}

    public static @NotNull Result run(@NotNull Project project, @NotNull List<String> args, @NotNull ProgressIndicator indicator) {
        try {
            var cmd = QilletniToolchainLauncher.toolchainCommandLine(project, args);
            // init runs `qpm install` itself; point it at the same JDK
            var jdk = QilletniInstallation.jdk(project);
            if (jdk != null && jdk.getHomePath() != null) cmd.withEnvironment("JAVA_HOME", jdk.getHomePath());
            indicator.setText(QilletniBundle.message("wizard.progress.init"));
            var output = new CapturingProcessHandler(cmd).runProcessWithProgressIndicator(indicator);
            return new Result(output.getExitCode() == 0 && !output.isCancelled(), output.getExitCode(), output.getStdout() + output.getStderr());
        } catch (ExecutionException e) {
            return new Result(false, -1, e.getMessage());
        }
    }
}
