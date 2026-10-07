package dev.qilletni.intellij.resolve.impl;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniYamlInfoUtil;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reverse resolver: given a Java PsiMethod, find Qilletni native function definitions in the current project
 * that bind to it. Constraints:
 * - Strictly project sources (never use jars)
 * - Only within qilletni-src roots whose qilletni_info.yml lists the method's containing class FQN
 * - Name-only matching
 */
public final class QilletniReverseNativeResolver {
    private QilletniReverseNativeResolver() {}

    /** The native functions declared under one project qilletni-src root, and the classes its manifest binds. */
    private record SrcRootNatives(Set<String> nativeClasses, Map<String, List<QilletniFunctionDef>> nativesByName) {}

    public static @NotNull List<QilletniFunctionDef> resolveQilletniFunctions(@NotNull PsiMethod method) {
        var project = method.getProject();
        if (DumbService.isDumb(project)) return List.of();

        var cls = method.getContainingClass();
        var fqn = cls != null ? cls.getQualifiedName() : null;
        if (fqn == null || fqn.isBlank()) return List.of();
        var name = method.getName();

        List<QilletniFunctionDef> results = new ArrayList<>();
        for (var root : getProjectNatives(project)) {
            if (!root.nativeClasses().contains(fqn)) continue;
            for (var def : root.nativesByName().getOrDefault(name, List.of())) {
                if (def.isValid()) results.add(def);
            }
        }
        return List.copyOf(results);
    }

    /** Computed once per project state, since Java line markers ask for every method of every open file. */
    private static List<SrcRootNatives> getProjectNatives(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            List<SrcRootNatives> roots = new ArrayList<>();
            for (var srcRoot : QilletniYamlInfoUtil.findProjectQilletniSrcRoots(project)) {
                var natives = QilletniYamlInfoUtil.readNativeClassesFromYaml(project, srcRoot);
                if (natives.isEmpty()) continue;
                roots.add(new SrcRootNatives(Set.copyOf(natives), collectNativeFunctions(project, srcRoot)));
            }
            return CachedValueProvider.Result.create(List.copyOf(roots),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    ProjectRootManager.getInstance(project),
                    QilletniProjectDependencies.getInstance(project).getModificationTracker());
        });
    }

    private static Map<String, List<QilletniFunctionDef>> collectNativeFunctions(Project project, VirtualFile root) {
        var psiManager = PsiManager.getInstance(project);
        Map<String, List<QilletniFunctionDef>> byName = new HashMap<>();
        VfsUtilCore.iterateChildrenRecursively(root, f -> true, fileOrDir -> {
            if (!fileOrDir.isDirectory() && fileOrDir.getName().endsWith(".ql")
                    && psiManager.findFile(fileOrDir) instanceof QilletniFile psi) {
                for (var def : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionDef.class)) {
                    if (def.getNative() == null) continue;
                    var fnName = def.getFunctionName();
                    var id = fnName != null ? fnName.getId() : null;
                    if (id != null) byName.computeIfAbsent(id.getText(), k -> new ArrayList<>()).add(def);
                }
            }
            return true;
        });
        return byName;
    }
}
