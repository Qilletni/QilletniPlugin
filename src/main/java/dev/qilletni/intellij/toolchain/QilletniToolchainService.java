package dev.qilletni.intellij.toolchain;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Checks that the configured toolchain actually starts, by running {@code --version} through
 * {@link QilletniToolchainLauncher}. The check runs in the background and is cached until the JAR or JDK changes.
 * <p>
 * Run configurations don't wait for this; they only use {@link QilletniToolchainLauncher#findProblem}, which is instant.
 */
@Service(Service.Level.PROJECT)
public final class QilletniToolchainService {
    private static final Logger LOG = Logger.getInstance(QilletniToolchainService.class);

    /** @param version the toolchain's version output when {@code ok}; @param problem why it failed otherwise */
    public record Status(boolean ok, @Nullable String version, @Nullable String problem) {}

    private final Project project;
    private volatile @Nullable String cachedKey;
    private volatile @Nullable Status cachedStatus;

    public QilletniToolchainService(@NotNull Project project) {
        this.project = project;
    }

    public static QilletniToolchainService getInstance(@NotNull Project project) {
        return project.getService(QilletniToolchainService.class);
    }

    /** The last completed check, if it still matches the current settings. */
    public @Nullable Status getCachedStatus() {
        return Objects.equals(cachedKey, currentKey()) ? cachedStatus : null;
    }

    /** Checks the toolchain in the background, reusing the cached result when nothing changed. */
    public @NotNull CompletableFuture<Status> checkAsync() {
        var cached = getCachedStatus();
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> {
            var key = currentKey();
            var status = check();
            cachedKey = key;
            cachedStatus = status;
            return status;
        }, AppExecutorUtil.getAppExecutorService());
    }

    private Status check() {
        var problem = QilletniToolchainLauncher.findProblem(project);
        if (problem != null) return new Status(false, null, problem);
        try {
            var cmd = QilletniToolchainLauncher.toolchainCommandLine(project, List.of("--version"));
            var output = new CapturingProcessHandler(cmd).runProcess(15_000);
            if (output.isTimeout()) return new Status(false, null, QilletniBundle.message("toolchain.problem.timeout"));
            var text = output.getStdout().trim();
            if (output.getExitCode() != 0 || text.isEmpty()) {
                var error = output.getStderr().trim();
                return new Status(false, null, error.isEmpty() ? QilletniBundle.message("toolchain.problem.exitCode", output.getExitCode()) : firstLine(error));
            }
            // The first line reads "qilletni <version>"; the rest lists component versions
            var version = firstLine(text);
            if (version.startsWith("qilletni ")) version = version.substring("qilletni ".length()).trim();
            return new Status(true, version, null);
        } catch (ExecutionException e) {
            LOG.info("Qilletni toolchain check failed", e);
            return new Status(false, null, e.getMessage());
        }
    }

    /** Identifies the inputs of a check: JAR path and modification time, and JDK home. */
    private @Nullable String currentKey() {
        var jar = QilletniInstallation.toolchainJar();
        var jdk = QilletniInstallation.jdk(project);
        if (jar == null || jdk == null) return null;
        long modified;
        try {
            modified = Files.getLastModifiedTime(jar).toMillis();
        } catch (IOException e) {
            modified = -1;
        }
        return jar + "|" + modified + "|" + jdk.getHomePath();
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(0, newline).trim() : text;
    }
}
