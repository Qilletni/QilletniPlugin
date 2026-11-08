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
import dev.qilletni.intellij.execution.QilletniBuildCommandLineState;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Builds a library into a .qll with {@code qilletni build}. The target is the project directory. */
public final class QilletniBuildRunConfiguration extends QilletniRunConfigurationBase {
    /** Where to write the .qll; blank uses the toolchain default ({@code build/ql-build}). */
    public String outputDir = "";
    /** Whether to rebuild native.jar with Gradle; {@code false} passes {@code --no-build-jar}. */
    public boolean rebuildNativeJar = true;
    /** Passes {@code --gradle-verbose}. */
    public boolean verboseGradle = false;

    QilletniBuildRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new QilletniBuildRunConfigurationEditor(getProject());
    }

    @Override
    public @NotNull RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        checkExecutor(executor);
        return new QilletniBuildCommandLineState(environment, this);
    }

    /** The archive written by the last build when a custom output is set; {@code null} means the default location. */
    public @Nullable Path findBuiltArchive() {
        if (outputDir.isBlank()) return null;
        var output = Path.of(outputDir);
        if (outputDir.endsWith(".qll")) return output;
        try (var files = Files.list(output)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".qll"))
                    .max(Comparator.comparingLong(f -> f.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        outputDir = JDOMExternalizerUtil.readField(element, "outputDir", "");
        rebuildNativeJar = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "rebuildNativeJar", "true"));
        verboseGradle = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "verboseGradle", "false"));
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, "outputDir", outputDir);
        JDOMExternalizerUtil.writeField(element, "rebuildNativeJar", Boolean.toString(rebuildNativeJar));
        JDOMExternalizerUtil.writeField(element, "verboseGradle", Boolean.toString(verboseGradle));
    }
}
