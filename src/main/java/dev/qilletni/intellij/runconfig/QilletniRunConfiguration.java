package dev.qilletni.intellij.runconfig;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import dev.qilletni.intellij.execution.QilletniCommandLineState;
import dev.qilletni.intellij.toolchain.QilletniToolchainService;
import dev.qilletni.intellij.toolchain.QilletniToolchainSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class QilletniRunConfiguration extends RunConfigurationBase<Object> {
    public String targetPath = "";
    public String args = "";
    public Map<String, String> env = new HashMap<>();
    public String workingDir = "";
    public String toolchainOverride = "";
    public boolean useNativeJar = false;
    /** Optional path to a local library directory. */
    public String localLibrary = "";

    protected QilletniRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new QilletniRunConfigurationEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (StringUtil.isEmptyOrSpaces(targetPath)) {
            throw new RuntimeConfigurationError("Target path is required");
        }

        var file = new File(targetPath);
        if (!file.exists()) {
            throw new RuntimeConfigurationError("Target does not exist");
        }

        var projectSdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
        if (projectSdk == null) {
            throw new RuntimeConfigurationError("No Project SDK configured.");
        }

        var toolchain = QilletniToolchainService.getInstance(getProject());
        var result = toolchain.validate();
        if (!result.ok()) {
            throw new RuntimeConfigurationError(result.message() != null ? result.message() : "Invalid toolchain");
        }
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
        return new QilletniCommandLineState(environment, this);
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        targetPath = JDOMExternalizerUtil.readField(element, "targetPath", "");
        args = JDOMExternalizerUtil.readField(element, "args", "");
        workingDir = JDOMExternalizerUtil.readField(element, "workingDir", "");
        toolchainOverride = JDOMExternalizerUtil.readField(element, "toolchainOverride", "");
        useNativeJar = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "useNativeJar", "false"));
        localLibrary = JDOMExternalizerUtil.readField(element, "localLibrary", "");
        // env is omitted for brevity; IDE serializes environment via EnvironmentVariablesComponent in editor
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, "targetPath", targetPath);
        JDOMExternalizerUtil.writeField(element, "args", args);
        JDOMExternalizerUtil.writeField(element, "workingDir", workingDir);
        JDOMExternalizerUtil.writeField(element, "toolchainOverride", toolchainOverride);
        JDOMExternalizerUtil.writeField(element, "useNativeJar", Boolean.toString(useNativeJar));
        JDOMExternalizerUtil.writeField(element, "localLibrary", localLibrary);
    }
}
