package dev.qilletni.intellij.runconfig;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import dev.qilletni.intellij.execution.QilletniCommandLineState;
import dev.qilletni.intellij.execution.QilletniLogStream;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/** Runs a .ql file with {@code qilletni run}. */
public final class QilletniRunConfiguration extends QilletniRunConfigurationBase {
    /** When running a library's example, the library's root directory; blank otherwise. */
    public String localLibrary = "";
    /** Whether the example should use the library's built native.jar. Only applies with {@link #localLibrary}. */
    public boolean useNativeJar = false;
    /** The lowest toolchain log level shown in the Run console: {@code INFO} or {@code DEBUG}. */
    public QilletniLogStream.Level logLevel = QilletniLogStream.Level.INFO;

    QilletniRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new QilletniRunConfigurationEditor(getProject());
    }

    @Override
    public @NotNull RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        checkExecutor(executor);
        return new QilletniCommandLineState(environment, this);
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        useNativeJar = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "useNativeJar", "false"));
        localLibrary = JDOMExternalizerUtil.readField(element, "localLibrary", "");
        logLevel = "DEBUG".equals(JDOMExternalizerUtil.readField(element, "logLevel")) ? QilletniLogStream.Level.DEBUG : QilletniLogStream.Level.INFO;
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, "useNativeJar", Boolean.toString(useNativeJar));
        JDOMExternalizerUtil.writeField(element, "localLibrary", localLibrary);
        JDOMExternalizerUtil.writeField(element, "logLevel", logLevel.name());
    }
}
