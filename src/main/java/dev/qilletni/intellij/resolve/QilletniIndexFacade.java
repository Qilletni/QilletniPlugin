package dev.qilletni.intellij.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.index.QilletniEntityIndex;
import dev.qilletni.intellij.index.QilletniExtMethodIndex;
import dev.qilletni.intellij.index.QilletniIndexConstants;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniEntityName;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniFunctionName;
import dev.qilletni.intellij.psi.QilletniFunctionOnType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lookup facade scoped to the current file and its imports.
 * Consults the stub indexes first and falls back to scanning PSI. Results are cached per context file.
 */
public final class QilletniIndexFacade {
    private QilletniIndexFacade() {}

    /** Receiver key of extension methods offered when the receiver type is unknown. */
    private static final String ANY_RECEIVER = "ANY";

    private static final Key<CachedValue<Map<String, QilletniEntityDef>>> ENTITY_CACHE_KEY =
            Key.create("qilletni.index.entity.byType");
    private static final Key<CachedValue<Map<String, List<QilletniFunctionDef>>>> EXT_METHODS_CACHE_KEY =
            Key.create("qilletni.index.ext.methods");
    private static final Key<CachedValue<List<QilletniEntityDef>>> ENTITY_LIST_CACHE_KEY =
            Key.create("qilletni.index.entities.in.scope");
    private static final Key<CachedValue<List<QilletniFunctionDef>>> TOP_LEVEL_FUNCS_CACHE_KEY =
            Key.create("qilletni.index.top.level.functions");
    private static final Key<CachedValue<Map<String, List<QilletniFunctionDef>>>> EXT_METHODS_BY_TYPE_CACHE_KEY =
            Key.create("qilletni.index.ext.methods.byType");

    public static QilletniEntityDef findEntityByTypeName(Project project, QilletniFile contextFile, String rawTypeName) {
        if (project == null || contextFile == null || rawTypeName == null) return null;

        Map<String, QilletniEntityDef> cache = getOrCreateMapCache(project, contextFile, ENTITY_CACHE_KEY);
        QilletniEntityDef cached = cache.get(rawTypeName);
        if (cached != null && cached.isValid()) return cached;

        var tn = TypeName.parse(rawTypeName);
        List<VirtualFile> files = candidateFilesForType(contextFile, tn);
        if (!files.isEmpty()) {
            var iter = StubIndex.getElements(QilletniEntityIndex.KEY, tn.simple(), project, filesScope(project, files), QilletniEntityDef.class).iterator();
            if (iter.hasNext()) {
                var found = iter.next();
                if (found != null && found.isValid()) {
                    cache.put(rawTypeName, found);
                    return found;
                }
            }
        }

        // Fallback PSI scan
        QilletniEntityDef found = null;
        for (VirtualFile vf : files) {
            var psi = findQilletniFile(project, vf);
            if (psi != null && (found = findEntityInFile(psi, tn.simple())) != null) break;
        }
        if (found == null && contextFile.getVirtualFile() == null && tn.qualifier() == null) {
            found = findEntityInFile(contextFile, tn.simple());
        }
        if (found != null) cache.put(rawTypeName, found);
        return found;
    }

    private static @Nullable QilletniEntityDef findEntityInFile(PsiFile psi, String simpleName) {
        for (var e : PsiTreeUtil.findChildrenOfType(psi, QilletniEntityDef.class)) {
            var name = PsiTreeUtil.findChildOfType(e, QilletniEntityName.class);
            if (name != null && name.getText().contentEquals(simpleName)) return e;
        }
        return null;
    }

    public static List<QilletniFunctionDef> findExtensionMethods(Project project, QilletniFile contextFile, String rawReceiverType, String methodName) {
        if (project == null || contextFile == null || rawReceiverType == null || methodName == null) return List.of();

        Map<String, List<QilletniFunctionDef>> cache = getOrCreateMapCache(project, contextFile, EXT_METHODS_CACHE_KEY);
        String cacheKey = rawReceiverType + "#" + methodName;
        List<QilletniFunctionDef> cached = validOnly(cache.get(cacheKey));
        if (!cached.isEmpty()) return cached;

        var tn = TypeName.parse(rawReceiverType);
        List<VirtualFile> files = candidateFilesForType(contextFile, tn);
        List<QilletniFunctionDef> result = new ArrayList<>();
        if (!files.isEmpty()) {
            result.addAll(queryExtMethodIndex(project, filesScope(project, files), tn.simple(), List.of(methodName)));
        }

        // Fallback PSI scan if the index didn't yield any
        if (result.isEmpty()) {
            Set<String> seen = new HashSet<>();
            Consumer<PsiFile> scan = psi -> {
                var vf = psi.getVirtualFile();
                for (var def : collectExtensionMethodDefs(psi, tn.simple())) {
                    var fName = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
                    if (fName == null || !fName.getText().contentEquals(methodName)) continue;
                    if (seen.add((vf != null ? vf.getPath() : "CTX") + ":" + fName.getText())) result.add(def);
                }
            };
            for (VirtualFile vf : files) {
                var psi = findQilletniFile(project, vf);
                if (psi != null) scan.accept(psi);
            }
            if (contextFile.getVirtualFile() == null) scan.accept(contextFile);
        }
        List<QilletniFunctionDef> immutable = validOnly(result);
        cache.put(cacheKey, immutable);
        return immutable;
    }

    /** All entities visible from the given file (current file + all imports). */
    public static List<QilletniEntityDef> listEntitiesInScope(Project project, QilletniFile contextFile) {
        return CachedValuesManager.getManager(project).getCachedValue(contextFile, ENTITY_LIST_CACHE_KEY, () -> {
            var list = new ArrayList<QilletniEntityDef>();
            forEachFileInScope(project, contextFile, psi -> list.addAll(PsiTreeUtil.findChildrenOfType(psi, QilletniEntityDef.class)));
            return CachedValueProvider.Result.create(List.copyOf(list), dependencies(project));
        }, false);
    }

    /** All top-level functions (no receiver, not inside an entity) visible from the given file. */
    public static List<QilletniFunctionDef> listTopLevelFunctions(Project project, QilletniFile contextFile) {
        return CachedValuesManager.getManager(project).getCachedValue(contextFile, TOP_LEVEL_FUNCS_CACHE_KEY, () -> {
            var list = new ArrayList<QilletniFunctionDef>();
            forEachFileInScope(project, contextFile, psi -> {
                for (var def : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionDef.class)) {
                    if (PsiTreeUtil.findChildOfType(def, QilletniFunctionOnType.class) != null) continue;
                    if (PsiTreeUtil.getParentOfType(def, QilletniEntityDef.class, false) != null) continue;
                    list.add(def);
                }
            });
            return CachedValueProvider.Result.create(List.copyOf(list), dependencies(project));
        }, false);
    }

    /**
     * All extension methods for a receiver type visible from the given file. With no receiver type, returns the
     * methods whose receiver is {@value #ANY_RECEIVER}.
     * <p>
     * The extension-method index is keyed by {@code Receiver#name}, so this enumerates the function names declared
     * in the candidate files and queries the index for each of them.
     */
    public static List<QilletniFunctionDef> listExtensionMethods(Project project, QilletniFile contextFile, String rawReceiverType) {
        Map<String, List<QilletniFunctionDef>> cache = getOrCreateMapCache(project, contextFile, EXT_METHODS_BY_TYPE_CACHE_KEY);

        boolean unknownType = rawReceiverType == null || rawReceiverType.isBlank();
        String cacheKey = unknownType ? ANY_RECEIVER : rawReceiverType;
        List<QilletniFunctionDef> cached = validOnly(cache.get(cacheKey));
        if (!cached.isEmpty()) return cached;

        var tn = unknownType ? new TypeName(null, ANY_RECEIVER) : TypeName.parse(rawReceiverType);
        List<VirtualFile> files = candidateFilesForType(contextFile, tn);
        boolean noVirtualFile = contextFile.getVirtualFile() == null;
        List<QilletniFunctionDef> list = new ArrayList<>();
        if (unknownType && files.isEmpty() && noVirtualFile) {
            // Scratch/unsaved: only the context PSI
            list.addAll(collectExtensionMethodDefs(contextFile, tn.simple()));
        } else {
            if (!files.isEmpty()) {
                Set<String> names = new LinkedHashSet<>();
                for (VirtualFile vf : files) {
                    var psi = findQilletniFile(project, vf);
                    if (psi == null) continue;
                    for (var fn : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionName.class)) {
                        var id = fn.getId();
                        if (id != null) names.add(id.getText());
                    }
                }
                list.addAll(queryExtMethodIndex(project, filesScope(project, files), tn.simple(), names));
            }
            if (!unknownType && noVirtualFile) {
                list.addAll(collectExtensionMethodDefs(contextFile, tn.simple()));
            }
        }
        List<QilletniFunctionDef> immutable = validOnly(list);
        cache.put(cacheKey, immutable);
        return immutable;
    }

    private static List<QilletniFunctionDef> queryExtMethodIndex(Project project, GlobalSearchScope scope, String receiverSimple, Collection<String> names) {
        List<QilletniFunctionDef> out = new ArrayList<>();
        for (var name : names) {
            String key = QilletniIndexConstants.extMethodKey(receiverSimple, name);
            if (key.isBlank()) continue;
            out.addAll(StubIndex.getElements(QilletniExtMethodIndex.KEY, key, project, scope, QilletniFunctionDef.class));
        }
        return out;
    }

    /** Extension methods in {@code psi} whose receiver type token reads {@code receiverSimple}. */
    private static List<QilletniFunctionDef> collectExtensionMethodDefs(PsiFile psi, String receiverSimple) {
        List<QilletniFunctionDef> out = new ArrayList<>();
        for (var def : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionDef.class)) {
            var receiver = QilletniPsiUtil.receiverTypeToken(PsiTreeUtil.findChildOfType(def, QilletniFunctionOnType.class));
            if (receiver != null && receiver.getText().contentEquals(receiverSimple)) out.add(def);
        }
        return out;
    }

    /** Visits the context file and every file it imports. A context file without a VirtualFile is visited last. */
    private static void forEachFileInScope(Project project, QilletniFile contextFile, Consumer<PsiFile> action) {
        var files = new ArrayList<VirtualFile>();
        if (contextFile.getVirtualFile() != null) files.add(contextFile.getVirtualFile());
        files.addAll(QilletniAliasResolver.getAllImportedFiles(contextFile));
        for (var vf : files) {
            var psi = findQilletniFile(project, vf);
            if (psi != null) action.accept(psi);
        }
        if (contextFile.getVirtualFile() == null) action.accept(contextFile);
    }

    private static List<VirtualFile> candidateFilesForType(QilletniFile contextFile, TypeName tn) {
        if (tn.qualifier() != null) {
            // Qualified name: strictly the files imported under this alias
            return QilletniAliasResolver.getFilesForQualifier(contextFile, tn.qualifier());
        }
        // Unqualified: current file + all imported files (project + libraries + auto-imports)
        List<VirtualFile> res = new ArrayList<>();
        if (contextFile.getVirtualFile() != null) res.add(contextFile.getVirtualFile());
        res.addAll(QilletniAliasResolver.getAllImportedFiles(contextFile));
        return res;
    }

    private static @Nullable QilletniFile findQilletniFile(Project project, @Nullable VirtualFile vf) {
        return vf != null && PsiManager.getInstance(project).findFile(vf) instanceof QilletniFile qf ? qf : null;
    }

    private static GlobalSearchScope filesScope(Project project, Collection<VirtualFile> files) {
        return GlobalSearchScope.filesScope(project, new HashSet<>(files));
    }

    private static <T extends PsiElement> List<T> validOnly(@Nullable List<T> elements) {
        if (elements == null) return List.of();
        return elements.stream().filter(e -> e != null && e.isValid()).toList();
    }

    /** A per-file map cache, filled lazily from concurrent read actions. */
    private static <V> Map<String, V> getOrCreateMapCache(Project project, QilletniFile file, Key<CachedValue<Map<String, V>>> key) {
        return CachedValuesManager.getManager(project).getCachedValue(file, key, () ->
                CachedValueProvider.Result.create(new ConcurrentHashMap<>(), dependencies(project)), false);
    }

    /** Scope results change with any PSI edit, with library roots, and with the resolved project dependencies. */
    private static Object[] dependencies(Project project) {
        return new Object[]{
                PsiModificationTracker.MODIFICATION_COUNT,
                QilletniAliasResolver.rootsTracker(),
                QilletniProjectDependencies.getInstance(project).getModificationTracker()
        };
    }

    private record TypeName(@Nullable String qualifier, String simple) {
        static TypeName parse(String raw) {
            int dot = raw.indexOf('.');
            if (dot > 0 && dot < raw.length() - 1) {
                return new TypeName(raw.substring(0, dot), raw.substring(dot + 1));
            }
            return new TypeName(null, raw);
        }
    }
}
