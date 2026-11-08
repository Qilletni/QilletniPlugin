package dev.qilletni.intellij.gutter;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.PsiNavigateUtil;
import dev.qilletni.intellij.yaml.YamlNativeClassesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLScalar;

import javax.swing.*;

/**
 * Shows a gutter icon on qilletni_info.yml native_classes entries that resolve to a project Java class.
 */
public class QilletniYamlNativeClassLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof YAMLScalar scalar)) return null;
        if (!YamlNativeClassesUtil.isInNativeClasses(scalar)) return null;
        var project = scalar.getProject();
        if (DumbService.isDumb(project)) return null;

        // Use PsiReferenceService as per guidelines
        var refs = PsiReferenceService.getService().getReferences(scalar, PsiReferenceService.Hints.NO_HINTS);
        PsiElement target = null;
        for (var ref : refs) {
            var resolved = ref.resolve();
            if (resolved != null) { target = resolved; break; }
        }
        if (target == null) return null;
        final PsiElement navTarget = target;

        Icon icon = AllIcons.Gutter.ImplementedMethod;
        String tooltip = "Navigate to Java class";
        // LineMarker should be registered on a leaf element for performance; pick the first leaf inside the scalar
        PsiElement anchor = scalar;
        while (anchor.getFirstChild() != null) {
            anchor = anchor.getFirstChild();
        }
        // Fallback safety
        if (anchor == null) anchor = scalar;

        return new LineMarkerInfo<>(
                anchor,
                anchor.getTextRange(),
                icon,
                psi -> tooltip,
                (e, elt) -> PsiNavigateUtil.navigate(navTarget),
                GutterIconRenderer.Alignment.CENTER,
                () -> "Qilletni native class target"
        );
    }
}
