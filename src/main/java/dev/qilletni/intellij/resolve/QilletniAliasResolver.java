package dev.qilletni.intellij.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.library.QilletniProjectDependencies;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Alias-aware resolution facade for imported files.
 * Uses CachedValues per file to avoid rescanning PSI on every query.
 * External dependency resolution can be added later without changing callers.
 */
public final class QilletniAliasResolver {
    private QilletniAliasResolver() {}

    private static final SimpleModificationTracker ROOTS_TRACKER = new SimpleModificationTracker();
    public static void onRootsChanged() { ROOTS_TRACKER.incModificationCount(); }
    public static ModificationTracker rootsTracker() { return ROOTS_TRACKER; }

    private static final Key<CachedValue<AliasData>> KEY = Key.create("qilletni.alias.data");

    public static List<VirtualFile> getFilesForQualifier(QilletniFile contextFile, String qualifier) {
        // Alias-qualified names are re-exported transitively.
        return getData(contextFile).aliasToFiles().getOrDefault(qualifier, List.of());
    }

    public static List<VirtualFile> getAllImportedFiles(QilletniFile contextFile) {
        return getData(contextFile).allImports();
    }

    private static AliasData getData(QilletniFile contextFile) {
        return CachedValuesManager.getCachedValue(contextFile, KEY, () -> {
            // Build transitive alias maps: each alias expands to its transitive closure.
            Map<String, List<VirtualFile>> directAliased = QilletniImportUtil.collectAliasedImports(contextFile);
            Map<String, List<VirtualFile>> aliased = new LinkedHashMap<>();
            for (String alias : directAliased.keySet()) {
                // Re-export alias-qualified names transitively (recursive). Project-local scope applied.
                aliased.put(alias, List.copyOf(QilletniImportUtil.getTransitiveFilesForQualifier(contextFile, alias)));
            }
            // All imports (project-local + libraries), transitively
            var all = List.copyOf(new LinkedHashSet<>(QilletniImportUtil.getTransitiveAllImportedFiles(contextFile)));
            AliasData value = new AliasData(aliased, all);
            var dependencies = QilletniProjectDependencies.getInstance(contextFile.getProject()).getModificationTracker();
            return CachedValueProvider.Result.create(value, PsiModificationTracker.MODIFICATION_COUNT, ROOTS_TRACKER, dependencies);
        });
    }

    private record AliasData(Map<String, List<VirtualFile>> aliasToFiles, List<VirtualFile> allImports) {}
}
