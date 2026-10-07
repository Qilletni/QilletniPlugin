package dev.qilletni.intellij.runconfig;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniProjectModel;
import dev.qilletni.intellij.library.model.QilletniManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Creates "Build <project>" from qilletni_info.yml or the Qilletni project directory. */
public final class QilletniBuildRunConfigurationProducer extends LazyRunConfigurationProducer<QilletniBuildRunConfiguration> {

    @Override
    protected boolean setupConfigurationFromContext(@NotNull QilletniBuildRunConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        var model = findModel(context);
        if (model == null) return false;
        configuration.targetPath = model.rootDir().getPath();
        configuration.setName(QilletniBundle.message("build.configName", model.displayName()));
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull QilletniBuildRunConfiguration configuration, @NotNull ConfigurationContext context) {
        var model = findModel(context);
        return model != null && !configuration.targetPath.isBlank()
                && Path.of(configuration.targetPath).equals(model.rootDir().toNioPath());
    }

    /** The Qilletni project when the context is its manifest, its qilletni-src directory or its root directory. */
    private static @Nullable QilletniProjectModel findModel(ConfigurationContext context) {
        var location = context.getPsiLocation();
        if (location == null) return null;
        VirtualFile file = location instanceof com.intellij.psi.PsiFileSystemItem item ? item.getVirtualFile()
                : location.getContainingFile() != null ? location.getContainingFile().getVirtualFile() : null;
        if (file == null) return null;
        var model = QilletniProjectDependencies.getInstance(context.getProject()).findProject(file);
        if (model == null) return null;
        boolean matches = file.equals(model.rootDir()) || file.equals(model.srcRoot()) || file.getName().equals(QilletniManifest.FILE_NAME);
        return matches ? model : null;
    }

    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
        return QilletniBuildRunConfigurationType.getInstance().getConfigurationFactories()[0];
    }
}
