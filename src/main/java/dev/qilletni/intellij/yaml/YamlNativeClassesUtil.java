package dev.qilletni.intellij.yaml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import dev.qilletni.intellij.library.QilletniYamlInfoUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.qilletni.intellij.util.QilletniConstants.MANIFEST_FILE;
import static dev.qilletni.intellij.util.QilletniConstants.NATIVE_CLASSES_KEY;

/**
 * Helpers for the {@code native_classes} list of qilletni_info.yml.
 */
public final class YamlNativeClassesUtil {
    private YamlNativeClassesUtil() {}

    public static boolean isQilletniInfoYaml(@Nullable PsiFile file) {
        return file != null && MANIFEST_FILE.equals(file.getName());
    }

    /** Returns true if the element is a YAMLScalar that is part of the top-level native_classes sequence. */
    public static boolean isInNativeClasses(@NotNull PsiElement element) {
        if (!(element instanceof YAMLScalar)) return false;
        var file = element.getContainingFile();
        if (!isQilletniInfoYaml(file)) return false;

        PsiElement p = element.getParent();
        if (!(p instanceof YAMLSequenceItem)) return false;
        p = p.getParent();
        if (!(p instanceof YAMLSequence seq)) return false;
        p = seq.getParent();
        if (!(p instanceof YAMLKeyValue kv)) return false;
        if (!NATIVE_CLASSES_KEY.equals(kv.getKeyText())) return false;
        // Ensure it's at document root mapping (tolerate either direct document or top-level mapping)
        PsiElement rootParent = kv.getParent();
        if (rootParent instanceof YAMLDocument) return true;
        return rootParent instanceof YAMLMapping && rootParent.getParent() instanceof YAMLDocument;
    }

    /**
     * The native_classes entries naming {@code fqn} in the manifests of the project's own qilletni-src roots.
     * The lookup table is cached per project, since Java line markers query it for every class.
     */
    public static @NotNull List<YAMLScalar> findNativeClassEntries(@NotNull Project project, @NotNull String fqn) {
        var entries = getNativeClassEntries(project).getOrDefault(fqn, List.of());
        return entries.stream().filter(PsiElement::isValid).toList();
    }

    private static Map<String, List<YAMLScalar>> getNativeClassEntries(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            Map<String, List<YAMLScalar>> byFqn = new HashMap<>();
            var psiManager = PsiManager.getInstance(project);
            for (var srcRoot : QilletniYamlInfoUtil.findProjectQilletniSrcRoots(project)) {
                var yamlVf = srcRoot.findChild(MANIFEST_FILE);
                if (yamlVf != null && psiManager.findFile(yamlVf) instanceof YAMLFile yamlFile) {
                    for (var scalar : nativeClassScalars(yamlFile)) {
                        var text = scalar.getTextValue().trim();
                        if (!text.isEmpty()) byFqn.computeIfAbsent(text, k -> new ArrayList<>()).add(scalar);
                    }
                }
            }
            return CachedValueProvider.Result.create(byFqn, PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
        });
    }

    /** The scalar entries of the top-level native_classes sequence. */
    private static List<YAMLScalar> nativeClassScalars(YAMLFile yamlFile) {
        List<YAMLScalar> out = new ArrayList<>();
        for (YAMLDocument doc : yamlFile.getDocuments()) {
            if (!(doc.getTopLevelValue() instanceof YAMLMapping mapping)) continue;
            YAMLKeyValue kv = mapping.getKeyValueByKey(NATIVE_CLASSES_KEY);
            if (kv == null || !(kv.getValue() instanceof YAMLSequence seq)) continue;
            for (YAMLSequenceItem item : seq.getItems()) {
                if (item.getValue() instanceof YAMLScalar scalar) out.add(scalar);
            }
        }
        return out;
    }
}
