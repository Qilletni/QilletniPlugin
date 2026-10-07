package dev.qilletni.intellij.actions;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.project.Project;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.QilletniProjectModel;
import dev.qilletni.intellij.packages.QilletniPackageCommands;
import dev.qilletni.intellij.runconfig.QilletniBuildRunConfiguration;
import dev.qilletni.intellij.runconfig.QilletniBuildRunConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Package-management actions shown in Tools | Qilletni and the Qilletni tool window. */
public final class QilletniPackageActions {
    private QilletniPackageActions() {}

    /** Tools | Qilletni | Sync Dependencies: {@code qpm install}. */
    public static final class Sync extends QilletniProjectAction {
        @Override
        protected void perform(@NotNull Project project, @NotNull QilletniProjectModel model) {
            QilletniPackageCommands.install(project, model);
        }
    }

    /** Regenerates qilletni.lock from qilletni_info.yml. */
    public static final class UpdateLockfile extends QilletniProjectAction {
        @Override
        protected void perform(@NotNull Project project, @NotNull QilletniProjectModel model) {
            QilletniPackageCommands.updateLockfile(project, model);
        }
    }

    /** Builds the package with the project's Build configuration, creating one if needed. */
    public static final class Build extends QilletniProjectAction {
        @Override
        protected void perform(@NotNull Project project, @NotNull QilletniProjectModel model) {
            ProgramRunnerUtil.executeConfiguration(findOrCreateBuildConfiguration(project, model), DefaultRunExecutor.getRunExecutorInstance());
        }
    }

    public static final class PublishLocal extends QilletniProjectAction {
        @Override
        protected void perform(@NotNull Project project, @NotNull QilletniProjectModel model) {
            QilletniPackageCommands.publishLocal(project, model.rootDir().toNioPath(), builtArchive(project, model));
        }
    }

    public static final class Publish extends QilletniProjectAction {
        @Override
        protected void perform(@NotNull Project project, @NotNull QilletniProjectModel model) {
            QilletniPackageCommands.publish(project, model.rootDir().toNioPath(), builtArchive(project, model));
        }
    }

    public static final class GenerateDocs extends QilletniProjectAction {
        @Override
        protected void perform(@NotNull Project project, @NotNull QilletniProjectModel model) {
            QilletniPackageCommands.generateDocs(project, model);
        }
    }

    static @NotNull RunnerAndConfigurationSettings findOrCreateBuildConfiguration(@NotNull Project project, @NotNull QilletniProjectModel model) {
        var existing = findBuildConfiguration(project, model);
        if (existing != null) return existing;
        var runManager = RunManager.getInstance(project);
        var type = QilletniBuildRunConfigurationType.getInstance();
        var settings = runManager.createConfiguration(QilletniBundle.message("build.configName", model.displayName()), type.getConfigurationFactories()[0]);
        ((QilletniBuildRunConfiguration) settings.getConfiguration()).targetPath = model.rootDir().getPath();
        runManager.addConfiguration(settings);
        return settings;
    }

    private static @Nullable RunnerAndConfigurationSettings findBuildConfiguration(@NotNull Project project, @NotNull QilletniProjectModel model) {
        var root = model.rootDir().toNioPath();
        for (var settings : RunManager.getInstance(project).getConfigurationSettingsList(QilletniBuildRunConfigurationType.getInstance())) {
            if (settings.getConfiguration() instanceof QilletniBuildRunConfiguration cfg
                    && !cfg.targetPath.isBlank() && Path.of(cfg.targetPath).equals(root)) {
                return settings;
            }
        }
        return null;
    }

    /** The archive the project's Build configuration writes when it uses a custom output; {@code null} for the default. */
    private static @Nullable Path builtArchive(@NotNull Project project, @NotNull QilletniProjectModel model) {
        var settings = findBuildConfiguration(project, model);
        return settings != null ? ((QilletniBuildRunConfiguration) settings.getConfiguration()).findBuiltArchive() : null;
    }
}
