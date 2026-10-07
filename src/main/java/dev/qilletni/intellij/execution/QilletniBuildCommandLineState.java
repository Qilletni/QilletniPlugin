package dev.qilletni.intellij.execution;

import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.events.impl.OutputBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;
import com.intellij.util.execution.ParametersListUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.packages.QilletniPackageNotifications;
import dev.qilletni.intellij.runconfig.QilletniBuildRunConfiguration;
import dev.qilletni.intellij.toolchain.QilletniToolchainLauncher;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code qilletni build}. Progress is reported in the Build tool window: warnings and errors become nodes in the
 * build tree, and the result is decided by the exit code.
 */
public final class QilletniBuildCommandLineState extends JavaCommandLineState {
    private final QilletniBuildRunConfiguration cfg;
    private final Object buildId = new Object();
    private QilletniLogStream logStream;

    public QilletniBuildCommandLineState(@NotNull ExecutionEnvironment env, @NotNull QilletniBuildRunConfiguration cfg) {
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

        var args = buildArgs(cfg, logStream.getPort());

        var params = QilletniToolchainLauncher.javaParameters(getEnvironment().getProject(), args);
        params.setWorkingDirectory(cfg.targetPath);
        params.setEnv(cfg.env.getEnvs());
        params.setPassParentEnvs(cfg.env.isPassParentEnvs());
        return params;
    }

    /** Arguments for {@code qilletni build}: options first, then the project directory. */
    static @NotNull List<String> buildArgs(@NotNull QilletniBuildRunConfiguration cfg, int logPort) {
        var args = new ArrayList<String>();
        args.add("build");
        args.add("--log-port");
        args.add(Integer.toString(logPort));
        if (!cfg.outputDir.isBlank()) {
            args.add("--output-file");
            args.add(cfg.outputDir);
        }
        if (!cfg.rebuildNativeJar) args.add("--no-build-jar");
        if (cfg.verboseGradle) args.add("--gradle-verbose");
        args.addAll(ParametersListUtil.parse(cfg.args));
        args.add(cfg.targetPath);
        return args;
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

        var project = getEnvironment().getProject();
        var buildView = project.getService(BuildViewManager.class);
        var title = QilletniBundle.message("build.title", cfg.getName());
        var descriptor = new DefaultBuildDescriptor(buildId, title, cfg.targetPath, System.currentTimeMillis())
                .withExecutionEnvironment(getEnvironment());
        buildView.onEvent(buildId, new StartBuildEventImpl(descriptor, QilletniBundle.message("build.running")));

        logStream.attach(handler, record -> {
            buildView.onEvent(buildId, new OutputBuildEventImpl(buildId, record.formatLine() + "\n", record.level() != QilletniLogStream.Level.ERROR));
            if (record.level() == QilletniLogStream.Level.ERROR || record.level() == QilletniLogStream.Level.WARN) {
                var kind = record.level() == QilletniLogStream.Level.ERROR ? MessageEvent.Kind.ERROR : MessageEvent.Kind.WARNING;
                buildView.onEvent(buildId, new MessageEventImpl(buildId, kind, null, record.message(), record.stackTrace()));
            }
        });

        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                // Argument errors are printed before logging is set up, so mirror the process output too
                if (outputType == ProcessOutputTypes.SYSTEM) return;
                buildView.onEvent(buildId, new OutputBuildEventImpl(buildId, event.getText(), outputType != ProcessOutputTypes.STDERR));
            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                boolean success = event.getExitCode() == 0;
                buildView.onEvent(buildId, new FinishBuildEventImpl(buildId, null, System.currentTimeMillis(),
                        QilletniBundle.message(success ? "build.finished" : "build.failed"),
                        success ? new SuccessResultImpl() : new FailureResultImpl()));
                if (success) {
                    QilletniProjectDependencies.refreshAll();
                    QilletniPackageNotifications.buildSucceeded(project, cfg.targetPath, cfg.findBuiltArchive());
                }
            }
        });
        return handler;
    }
}
