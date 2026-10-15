package dev.qilletni.intellij.gutter;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PsiNavigateUtil;
import dev.qilletni.intellij.resolve.impl.QilletniReverseNativeResolver;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Gutter for Java methods that have Qilletni native counterparts, in project sources or in the decompiled
 * native.jar of an installed library (see {@link QilletniReverseNativeResolver}).
 * - Name-only matching
 * - Chooser popup when multiple targets
 */
public class QilletniJavaToQilletniLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier id)) return null;
        if (!(id.getParent() instanceof PsiMethod method)) return null;
        if (method.getNameIdentifier() != id) return null;

        List<QilletniFunctionDef> targets = QilletniReverseNativeResolver.resolveQilletniFunctions(method);
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
                () -> "Qilletni native function(s)"
        );
    }

    private static void openChooser(MouseEvent e, List<QilletniFunctionDef> targets) {
        if (targets.size() == 1) {
            PsiNavigateUtil.navigate(targets.getFirst());
            return;
        }
        var arr = targets.toArray(new QilletniFunctionDef[0]);
        NavigationUtil.getPsiElementPopup(arr, "Qilletni native(s)").show(new RelativePoint(e));
    }

    private static String buildTooltip(List<QilletniFunctionDef> targets) {
        // e.g., Qilletni native: file.ql::name
        var sb = new StringBuilder();
        sb.append("Qilletni native: <code>");
        int shown = 0;
        for (var def : targets) {
            if (shown > 0) sb.append(", ");
            var file = def.getContainingFile();
            var fileName = file != null ? file.getName() : "<unknown>";
            var fn = def.getFunctionName();
            var id = fn != null ? fn.getId() : null;
            var name = id != null ? id.getText() : "<unnamed>";
            sb.append(fileName).append("::").append(name);
            shown++;
            if (shown == 3) break;
        }
        if (targets.size() > 3) sb.append(", …");
        return sb.append("</code>").toString();
    }
}
