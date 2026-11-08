package dev.qilletni.intellij.runconfig;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiElement;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import org.jetbrains.annotations.NotNull;

/** Creates "Run 'file.ql'" from the editor, the project view or the gutter. */
public final class QilletniRunConfigurationProducer extends LazyRunConfigurationProducer<QilletniRunConfiguration> {

    @Override
    protected boolean setupConfigurationFromContext(@NotNull QilletniRunConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        var location = context.getPsiLocation();
        if (location == null || !(location.getContainingFile() instanceof QilletniFile file)) return false;
        var vf = file.getVirtualFile();
        if (vf == null) return false;

        configuration.targetPath = vf.getPath();
        configuration.setName(vf.getName());
        // Examples of a library run against the library's own sources
        var model = QilletniProjectDependencies.getInstance(context.getProject()).findProject(vf);
        if (model != null) {
            var examples = model.rootDir().findChild("examples");
            if (examples != null && VfsUtilCore.isAncestor(examples, vf, true)) {
                configuration.localLibrary = model.rootDir().getPath();
            }
        }
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull QilletniRunConfiguration configuration, @NotNull ConfigurationContext context) {
        var location = context.getPsiLocation();
        var file = location != null ? location.getContainingFile() : null;
        return file != null && file.getVirtualFile() != null && StringUtil.equals(configuration.targetPath, file.getVirtualFile().getPath());
    }

    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
        return QilletniRunConfigurationType.getInstance().getConfigurationFactories()[0];
    }
}
