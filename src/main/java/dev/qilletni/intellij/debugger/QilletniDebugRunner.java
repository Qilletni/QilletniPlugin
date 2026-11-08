package dev.qilletni.intellij.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.debugger.dap.QilletniDapSession;
import dev.qilletni.intellij.execution.QilletniCommandLineState;
import dev.qilletni.intellij.runconfig.QilletniRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Debugs a Qilletni Run configuration: starts {@code qilletni run --debug-port <port>} and a debug session that the
 * toolchain connects back to. Registered first so the Java debugger never attaches to the toolchain's JVM instead.
 */
public final class QilletniDebugRunner extends GenericProgramRunner<RunnerSettings> {
    @Override
    public @NotNull String getRunnerId() {
        return "QilletniDebugRunner";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof QilletniRunConfiguration;
    }

    @Override
    protected @Nullable RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        if (!(state instanceof QilletniCommandLineState runState)) return null;
        FileDocumentManager.getInstance().saveAllDocuments();

        QilletniDapSession dap;
        try {
            dap = new QilletniDapSession();
        } catch (IOException e) {
            throw new ExecutionException(QilletniBundle.message("debugger.error.socket"), e);
        }
        runState.setDebugPort(dap.getPort());
        ExecutionResult result;
        try {
            result = runState.execute(environment.getExecutor(), this);
        } catch (ExecutionException | RuntimeException e) {
            dap.close();
            throw e;
        }

        var cfg = (QilletniRunConfiguration) environment.getRunProfile();
        var target = LocalFileSystem.getInstance().findFileByPath(cfg.targetPath);
        var mapper = QilletniDebugSourceMapper.forProgram(environment.getProject(), target);
        return XDebuggerManager.getInstance(environment.getProject()).startSession(environment, new XDebugProcessStarter() {
            @Override
            public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
                return new QilletniDebugProcess(session, dap, result, mapper);
            }
        }).getRunContentDescriptor();
    }
}
