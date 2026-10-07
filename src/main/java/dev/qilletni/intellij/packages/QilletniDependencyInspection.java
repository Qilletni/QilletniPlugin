package dev.qilletni.intellij.packages;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniProjectModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

/** Flags dependencies in {@code qilletni_info.yml} that aren't installed or aren't in the lockfile, with a fix. */
public final class QilletniDependencyInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!QilletniManifestDependencies.isManifest(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
        var model = QilletniProjectDependencies.getInstance(holder.getProject()).findProject(holder.getFile().getVirtualFile());
        if (model == null) return PsiElementVisitor.EMPTY_VISITOR;

        return new YamlPsiElementVisitor() {
            @Override
            public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
                if (QilletniManifestDependencies.findDependencyEntry(keyValue) != keyValue || keyValue.getKey() == null) return;
                var id = keyValue.getKeyText();
                var dependency = model.dependencies().stream().filter(d -> d.id().equalsIgnoreCase(id)).findFirst().orElse(null);
                if (dependency == null && model.status() == QilletniProjectModel.Status.LOCK_STALE) {
                    holder.registerProblem(keyValue.getKey(), QilletniBundle.message("inspection.dependency.notLocked", id),
                            ProblemHighlightType.WARNING, new InstallFix(true));
                } else if (dependency != null && dependency.isMissing()) {
                    holder.registerProblem(keyValue.getKey(), QilletniBundle.message("inspection.dependency.notInstalled", id),
                            ProblemHighlightType.WARNING, new InstallFix(false));
                }
            }
        };
    }

    private record InstallFix(boolean updateLockfile) implements LocalQuickFix {
        @Override
        public @NotNull String getFamilyName() {
            return QilletniBundle.message(updateLockfile ? "qpm.updateLock.ok" : "qpm.install.action");
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            var model = QilletniProjectDependencies.getInstance(project).findProject(descriptor.getPsiElement().getContainingFile().getVirtualFile());
            if (model == null) return;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (updateLockfile) QilletniPackageCommands.updateLockfile(project, model);
                else QilletniPackageCommands.install(project, model);
            });
        }
    }
}
