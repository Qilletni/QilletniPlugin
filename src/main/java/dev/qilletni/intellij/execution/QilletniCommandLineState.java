package dev.qilletni.intellij.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.util.execution.ParametersListUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.model.QilletniLockfile;
import dev.qilletni.intellij.runconfig.QilletniRunConfiguration;
import dev.qilletni.intellij.toolchain.QilletniToolchainLauncher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code qilletni run}. The program's output and the toolchain's log events share the Run console; log events
 * are colored by level. Only log events at or above the configuration's log level are shown.
 */
public final class QilletniCommandLineState extends JavaCommandLineState {
    private final QilletniRunConfiguration cfg;
    private QilletniLogStream logStream;
    private ConsoleView console;

    public QilletniCommandLineState(@NotNull ExecutionEnvironment env, @NotNull QilletniRunConfiguration cfg) {
        super(env);
        this.cfg = cfg;
        addConsoleFilters(new QilletniOutputHyperlinkFilter(env.getProject()));
    }

    @Override
    protected JavaParameters createJavaParameters() throws ExecutionException {
        try {
            logStream = new QilletniLogStream();
        } catch (IOException e) {
            throw new ExecutionException(QilletniBundle.message("runconfig.error.logServer"), e);
        }

        // Pass the lockfile explicitly so the run doesn't depend on the working directory
        var model = cfg.findQilletniProject();
        var lockfile = model != null ? model.srcRoot().findChild(QilletniLockfile.FILE_NAME) : null;
        var args = buildArgs(cfg, lockfile != null ? lockfile.getPath() : null, logStream.getPort());

        var params = QilletniToolchainLauncher.javaParameters(getEnvironment().getProject(), args);
        params.setWorkingDirectory(cfg.effectiveWorkingDir());
        params.setEnv(cfg.env.getEnvs());
        params.setPassParentEnvs(cfg.env.isPassParentEnvs());
        return params;
    }

    /** Arguments for {@code qilletni run}: options first, then the script and its own arguments. */
    static @NotNull List<String> buildArgs(@NotNull QilletniRunConfiguration cfg, @Nullable String lockfilePath, int logPort) {
        var args = new ArrayList<String>();
        args.add("run");
        if (!cfg.localLibrary.isBlank()) {
            args.add("--local-library");
            args.add(Path.of(cfg.localLibrary).toAbsolutePath().normalize().toString());
            if (cfg.useNativeJar) args.add("--use-native-jar");
        }
        if (lockfilePath != null) {
            args.add("--lockfile");
            args.add(lockfilePath);
        }
        args.add("--log-port");
        args.add(Integer.toString(logPort));
        args.add(cfg.targetPath);
        args.addAll(ParametersListUtil.parse(cfg.args));
        return args;
    }

    @Override
    protected @NotNull ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
        console = super.createConsole(executor);
        return console;
    }

    @Override
    protected @NotNull OSProcessHandler startProcess() throws ExecutionException {
        OSProcessHandler handler;
        try {
            handler = super.startProcess();
        } catch (ExecutionException | RuntimeException e) {
            if (logStream != null) logStream.close();
            throw e;
        }
        logStream.attach(handler, this::print);
        return handler;
    }

    /** Whether a toolchain log event at {@code level} belongs in the Run console for the chosen {@code threshold}. */
    static boolean shouldShow(@NotNull QilletniLogStream.Level level, @NotNull QilletniLogStream.Level threshold) {
        return level.compareTo(threshold) >= 0;
    }

    private void print(QilletniLogStream.LogRecord record) {
        if (console == null || !shouldShow(record.level(), cfg.logLevel)) return;
        var type = switch (record.level()) {
            case ERROR -> ConsoleViewContentType.LOG_ERROR_OUTPUT;
            case WARN -> ConsoleViewContentType.LOG_WARNING_OUTPUT;
            case INFO -> ConsoleViewContentType.LOG_INFO_OUTPUT;
            case DEBUG, TRACE -> ConsoleViewContentType.LOG_DEBUG_OUTPUT;
        };
        console.print(record.formatLine() + "\n", type);
        if (record.stackTrace() != null) console.print(record.stackTrace() + "\n", type);
    }
}
