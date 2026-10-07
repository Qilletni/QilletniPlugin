package dev.qilletni.intellij.yaml;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import org.jetbrains.annotations.Nullable;

/**
 * Adds YAML qilletni_info.yml native_classes entries as go-to targets when Ctrl/Cmd+Clicking a Java class name.
 * Strictly searches project-local qilletni-src roots only.
 */
public class QilletniJavaClassToYamlGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public @Nullable PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement element, int offset, Editor editor) {
        if (!(element instanceof PsiIdentifier id)) return null;
        PsiElement parent = id.getParent();
        if (!(parent instanceof PsiClass cls)) return null;
        if (cls.getNameIdentifier() != id) return null;
        Project project = cls.getProject();
        if (DumbService.isDumb(project)) return null;
        String fqn = cls.getQualifiedName();
        if (fqn == null || fqn.isBlank()) return null;

        var targets = YamlNativeClassesUtil.findNativeClassEntries(project, fqn);
        return targets.isEmpty() ? null : targets.toArray(PsiElement[]::new);
    }

    @Override
    public @Nullable String getActionText(DataContext context) {
        return null; // default
    }
}
