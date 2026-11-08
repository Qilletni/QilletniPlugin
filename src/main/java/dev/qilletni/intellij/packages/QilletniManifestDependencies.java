package dev.qilletni.intellij.packages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.library.model.QilletniManifest;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

/** Locates the {@code dependencies:} mapping of a {@code qilletni_info.yml}. */
final class QilletniManifestDependencies {
    private QilletniManifestDependencies() {}

    static boolean isManifest(@Nullable PsiElement element) {
        var file = element != null ? element.getContainingFile() : null;
        return file != null && QilletniManifest.FILE_NAME.equals(file.getOriginalFile().getName());
    }

    /** The top-level {@code dependencies} key-value containing the element, if any. */
    static @Nullable YAMLKeyValue findDependenciesSection(@Nullable PsiElement element) {
        for (var kv = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class); kv != null; kv = PsiTreeUtil.getParentOfType(kv, YAMLKeyValue.class)) {
            if ("dependencies".equals(kv.getKeyText()) && kv.getParent() instanceof YAMLMapping mapping && mapping.getParent() instanceof YAMLDocument) {
                return kv;
            }
        }
        return null;
    }

    /** The dependency entry ({@code scope/name: range}) containing the element, if it is one. */
    static @Nullable YAMLKeyValue findDependencyEntry(@Nullable PsiElement element) {
        var section = findDependenciesSection(element);
        if (section == null) return null;
        var kv = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class);
        return kv != null && kv != section && kv.getParentMapping() == section.getValue() ? kv : null;
    }
}
