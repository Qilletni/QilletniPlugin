package dev.qilletni.intellij.gutter;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import dev.qilletni.intellij.resolve.impl.QilletniNativeBindingResolver;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shows a gutter icon for native function definitions that bind to Java methods in native.jar.
 * Clicking the icon navigates to the bound Java method(s).
 */
public class QilletniNativeBindingLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof QilletniFunctionDef def)) return null;
        if (def.getNative() == null) return null; // only for native functions
        var nameElt = def.getFunctionName() != null ? def.getFunctionName().getId() : null;
        if (nameElt == null) return null;

        List<PsiMethod> targets = QilletniNativeBindingResolver.resolveJavaTargets(def);
        if (targets.isEmpty()) return null;

        Icon icon = AllIcons.Gutter.ImplementedMethod;
        var tooltip = buildTooltip(targets);

        return new LineMarkerInfo<>(
                nameElt,
                nameElt.getTextRange(),
                icon,
                psi -> tooltip,
                (e, elt) -> {
                    // If multiple, navigate to the first match for now.
                    targets.getFirst().navigate(true);
                },
                GutterIconRenderer.Alignment.CENTER,
                () -> "Binds to Java method(s)"
        );
    }

    private static String buildTooltip(List<PsiMethod> methods) {
        // Format: Binds to: ClassName.method(...)
        String list = methods.stream().limit(3).map(QilletniNativeBindingLineMarkerProvider::formatMethod).collect(Collectors.joining(", "));
        if (methods.size() > 3) list += ", …";
        return "Binds to: " + list;
    }

    private static String formatMethod(PsiMethod m) {
        var cls = m.getContainingClass();
        var clsName = cls != null ? (cls.getQualifiedName() != null ? cls.getQualifiedName() : cls.getName()) : "<unknown>";
        return clsName + "." + m.getName() + "(…)";
    }
}
