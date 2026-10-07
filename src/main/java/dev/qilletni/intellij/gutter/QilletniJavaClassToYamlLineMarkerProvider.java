package dev.qilletni.intellij.gutter;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.qilletni.intellij.yaml.YamlNativeClassesUtil;
import org.jetbrains.yaml.psi.YAMLScalar;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static dev.qilletni.intellij.util.QilletniConstants.MANIFEST_FILE;

/**
 * Shows a gutter on Java classes that are referenced in project-local qilletni_info.yml native_classes.
 */
public class QilletniJavaClassToYamlLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier id)) return null;
        if (!(id.getParent() instanceof PsiClass cls)) return null;
        if (cls.getNameIdentifier() != id) return null;
        var project = cls.getProject();
        if (DumbService.isDumb(project)) return null;
        var fqn = cls.getQualifiedName();
        if (fqn == null || fqn.isBlank()) return null;

        List<YAMLScalar> targets = YamlNativeClassesUtil.findNativeClassEntries(project, fqn);
        if (targets.isEmpty()) return null;

        Icon icon = AllIcons.Gutter.ImplementingMethod;
        String tooltip = buildTooltip(targets);
        return new LineMarkerInfo<>(
                id,
                id.getTextRange(),
                icon,
                psi -> tooltip,
                (e, elt) -> openChooser(e, targets),
                GutterIconRenderer.Alignment.CENTER,
                () -> "Listed in qilletni_info.yml native_classes"
        );
    }

    private static String buildTooltip(List<YAMLScalar> targets) {
        var sb = new StringBuilder("qilletni_info.yml native_classes: ");
        int shown = 0;
        for (var s : targets) {
            if (shown > 0) sb.append(", ");
            var file = s.getContainingFile();
            var fileName = file != null ? file.getName() : MANIFEST_FILE;
            sb.append(fileName);
            shown++;
            if (shown == 3) break;
        }
        if (targets.size() > 3) sb.append(", …");
        return sb.toString();
    }

    private static void openChooser(MouseEvent e, List<YAMLScalar> targets) {
        if (targets.size() == 1) {
            PsiNavigateUtil.navigate(targets.getFirst());
            return;
        }
        NavigationUtil.getPsiElementPopup(targets.toArray(PsiElement.EMPTY_ARRAY), "qilletni_info.yml usages").show(new RelativePoint(e));
    }
}
