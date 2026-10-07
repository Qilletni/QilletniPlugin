package dev.qilletni.intellij.runconfig;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.settings.QilletniConfigurable;
import dev.qilletni.intellij.toolchain.QilletniToolchainLauncher;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/** Settings shared by the Run and Build configurations: target, arguments, working directory and environment. */
public abstract class QilletniRunConfigurationBase extends RunConfigurationBase<Object> {
    public String targetPath = "";
    public String args = "";
    /** Blank means the Qilletni project directory containing the target. */
    public String workingDir = "";
    public EnvironmentVariablesData env = EnvironmentVariablesData.DEFAULT;

    protected QilletniRunConfigurationBase(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (StringUtil.isEmptyOrSpaces(targetPath)) {
            throw new RuntimeConfigurationError(QilletniBundle.message("runconfig.error.noTarget"));
        }
        if (!Files.exists(Path.of(targetPath))) {
            throw new RuntimeConfigurationError(QilletniBundle.message("runconfig.error.targetMissing", targetPath));
        }
        var problem = QilletniToolchainLauncher.findProblem(getProject());
        if (problem != null) {
            var error = new RuntimeConfigurationError(problem);
            error.setQuickFix(() -> ShowSettingsUtil.getInstance().showSettingsDialog(getProject(), QilletniConfigurable.class));
            throw error;
        }
    }

    /** Debugging attaches to the toolchain's JVM rather than the Qilletni program, so it is refused up front. */
    protected static void checkExecutor(@NotNull Executor executor) throws ExecutionException {
        if (DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId())) {
            throw new ExecutionException(QilletniBundle.message("runconfig.error.debug"));
        }
    }

    /** The configured working directory, else the Qilletni project directory of the target, else the project base. */
    public @Nullable String effectiveWorkingDir() {
        if (!StringUtil.isEmptyOrSpaces(workingDir)) return workingDir;
        var model = findQilletniProject();
        return model != null ? model.rootDir().getPath() : getProject().getBasePath();
    }

    /** The Qilletni project (directory with qilletni-src) containing the target, if known. */
    public @Nullable dev.qilletni.intellij.library.QilletniProjectModel findQilletniProject() {
        if (StringUtil.isEmptyOrSpaces(targetPath)) return null;
        var target = LocalFileSystem.getInstance().findFileByNioFile(Path.of(targetPath));
        return QilletniProjectDependencies.getInstance(getProject()).findProject(target);
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        targetPath = JDOMExternalizerUtil.readField(element, "targetPath", "");
        args = JDOMExternalizerUtil.readField(element, "args", "");
        workingDir = JDOMExternalizerUtil.readField(element, "workingDir", "");
        env = EnvironmentVariablesData.readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, "targetPath", targetPath);
        JDOMExternalizerUtil.writeField(element, "args", args);
        JDOMExternalizerUtil.writeField(element, "workingDir", workingDir);
        env.writeExternal(element);
    }
}
