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
import dev.qilletni.intellij.index.QilletniFunctionIndex;
import dev.qilletni.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Project-wide lookup facade scoped to the current file and its imports.
 * This scans PSI for now; it can be replaced by Stub/FileBased indices later without changing call sites.
 */
public final class QilletniIndexFacade {
    private QilletniIndexFacade() {}

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

        Map<String, QilletniEntityDef> cache = getOrCreateEntityCache(project, contextFile);
        QilletniEntityDef cached = cache.get(rawTypeName);
        if (cached != null && cached.isValid()) return cached;

        var tn = parseTypeName(rawTypeName);
        List<VirtualFile> files = candidateFilesForType(contextFile, tn);
        System.out.println("[DEBUG_LOG][IndexFacade] findEntityByTypeName raw='" + rawTypeName + "' simple='" + tn.simple + "' candidate files=" + files);
        // Try stub index first
        if (!files.isEmpty()) {
            GlobalSearchScope scope = GlobalSearchScope.filesScope(project, new java.util.HashSet<>(files));
            var iter = StubIndex.getElements(QilletniEntityIndex.KEY, tn.simple, project, scope, QilletniEntityDef.class).iterator();
            if (iter.hasNext()) {
                var found = iter.next();
                if (found != null && found.isValid()) {
                    cache.put(rawTypeName, found);
                    return found;
                }
            }
        }
        // Fallback PSI scan in specified files
        for (VirtualFile vf : files) {
            var psi = vf != null ? PsiManager.getInstance(project).findFile(vf) : null;
            if (!(psi instanceof QilletniFile)) continue;
            QilletniEntityDef e = getQilletniEntityDef(rawTypeName, cache, tn, psi);
            if (e != null) return e;
        }
        if (contextFile.getVirtualFile() == null && tn.qualifier == null) {
            return getQilletniEntityDef(rawTypeName, cache, tn, contextFile);
        }
        return null;
    }

    @Nullable
    private static QilletniEntityDef getQilletniEntityDef(String rawTypeName, Map<String, QilletniEntityDef> cache, TypeName tn, PsiFile psi) {
        for (var e : PsiTreeUtil.findChildrenOfType(psi, QilletniEntityDef.class)) {
            var name = PsiTreeUtil.findChildOfType(e, QilletniEntityName.class);
            if (name != null && name.getText().contentEquals(tn.simple)) {
                cache.put(rawTypeName, e);
                return e;
            }
        }
        return null;
    }

    public static List<QilletniFunctionDef> findExtensionMethods(Project project, QilletniFile contextFile, String rawReceiverType, String methodName) {
        List<QilletniFunctionDef> empty = List.of();
        if (project == null || contextFile == null || rawReceiverType == null || methodName == null) return empty;

        Map<String, List<QilletniFunctionDef>> cache = getOrCreateExtMethodsCache(project, contextFile);
        String k = rawReceiverType + "#" + methodName;
        List<QilletniFunctionDef> cached = cache.get(k);
        if (cached != null) {
            // Filter out invalid PSI just in case
            List<QilletniFunctionDef> valid = new ArrayList<>();
            for (var f : cached) if (f != null && f.isValid()) valid.add(f);
            if (!valid.isEmpty()) return List.copyOf(valid);
        }

        var tn = parseTypeName(rawReceiverType);
        List<VirtualFile> files = candidateFilesForType(contextFile, tn);
        // Try stub index first
        List<QilletniFunctionDef> result = new ArrayList<>();
        if (!files.isEmpty()) {
            var scope = GlobalSearchScope.filesScope(project, new java.util.HashSet<>(files));
            String key = dev.qilletni.intellij.index.QilletniIndexConstants.extMethodKey(tn.simple, methodName);
            if (!key.isBlank()) {
                var col = StubIndex.getElements(QilletniExtMethodIndex.KEY, key, project, scope, QilletniFunctionDef.class);
                for (var def : col) if (def != null && def.isValid()) result.add(def);
            }
        }
        // Fallback PSI scan if index didn’t yield any
        if (result.isEmpty()) {
            Set<String> seen = new HashSet<>();
            for (VirtualFile vf : files) {
                var psi = vf != null ? PsiManager.getInstance(project).findFile(vf) : null;
                if (!(psi instanceof QilletniFile)) continue;
                for (var def : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionDef.class)) {
                    var onType = PsiTreeUtil.findChildOfType(def, QilletniFunctionOnType.class);
                    if (onType == null) continue;
                    PsiElement id = PsiTreeUtil.findChildOfType(onType, PsiElement.class);
                    if (id == null || id.getNode() == null || id.getNode().getElementType() != QilletniTypes.ID) continue;
                    if (!id.getText().contentEquals(tn.simple)) continue;
                    var fName = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
                    if (fName == null || !fName.getText().contentEquals(methodName)) continue;
                    if (!def.isValid()) continue;
                    String sk = (vf != null ? vf.getPath() : "CTX") + ":" + fName.getText() + ":" + tn.simple;
                    if (seen.add(sk)) result.add(def);
                }
            }
            if (contextFile.getVirtualFile() == null) {
                for (var def : PsiTreeUtil.findChildrenOfType(contextFile, QilletniFunctionDef.class)) {
                    var onType = PsiTreeUtil.findChildOfType(def, QilletniFunctionOnType.class);
                    if (onType == null) continue;
                    PsiElement id = PsiTreeUtil.findChildOfType(onType, PsiElement.class);
                    if (id == null || id.getNode() == null || id.getNode().getElementType() != QilletniTypes.ID) continue;
                    if (!id.getText().contentEquals(tn.simple)) continue;
                    var fName = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
                    if (fName == null || !fName.getText().contentEquals(methodName)) continue;
                    String sk = "CTX:" + fName.getText() + ":" + tn.simple;
                    if (seen.add(sk)) result.add(def);
                }
            }
        }
        List<QilletniFunctionDef> immutable = List.copyOf(result);
        cache.put(k, immutable);
        return immutable;
    }

    // List all entities visible from the given file (current file + project-local imports and alias imports)
    public static List<QilletniEntityDef> listEntitiesInScope(Project project, QilletniFile contextFile) {
        return CachedValuesManager.getManager(project).getCachedValue(contextFile, ENTITY_LIST_CACHE_KEY, entitiesInScopeProvider(project, contextFile), false);
    }

    private static CachedValueProvider<List<QilletniEntityDef>> entitiesInScopeProvider(Project project, QilletniFile contextFile) {
        return () -> {
            var files = new ArrayList<VirtualFile>();
            if (contextFile.getVirtualFile() != null) files.add(contextFile.getVirtualFile());
            // Include all imports (project + libraries + auto-imports)
            files.addAll(QilletniAliasResolver.getAllImportedFiles(contextFile));

            var list = new ArrayList<QilletniEntityDef>();
            for (var vf : files) {
                var psi = vf != null ? PsiManager.getInstance(project).findFile(vf) : null;
                if (psi instanceof QilletniFile) {
                    list.addAll(PsiTreeUtil.findChildrenOfType(psi, QilletniEntityDef.class));
                }
            }
            if (contextFile.getVirtualFile() == null) {
                list.addAll(PsiTreeUtil.findChildrenOfType(contextFile, QilletniEntityDef.class));
            }
            return CachedValueProvider.Result.create(List.copyOf(list), PsiModificationTracker.MODIFICATION_COUNT, dev.qilletni.intellij.resolve.QilletniAliasResolver.rootsTracker());
        };
    }

    // List all top-level functions (no receiver/on-type) visible from the given file
    public static List<QilletniFunctionDef> listTopLevelFunctions(Project project, QilletniFile contextFile) {
        return CachedValuesManager.getManager(project).getCachedValue(contextFile, TOP_LEVEL_FUNCS_CACHE_KEY, topLevelFunctionsProvider(project, contextFile), false);
    }

    private static CachedValueProvider<List<QilletniFunctionDef>> topLevelFunctionsProvider(Project project, QilletniFile contextFile) {
        return () -> {
            var files = new ArrayList<VirtualFile>();
            if (contextFile.getVirtualFile() != null) files.add(contextFile.getVirtualFile());
            // Include all imports (project + libraries + auto-imports)
            files.addAll(QilletniAliasResolver.getAllImportedFiles(contextFile));

            var list = new ArrayList<QilletniFunctionDef>();
            if (!files.isEmpty()) {
                var scope = GlobalSearchScope.filesScope(project, new java.util.HashSet<>(files));
                // Not used currently; kept to show intended scope formation for future index-based lookup
            }
            for (var vf : files) {
                var psi = vf != null ? PsiManager.getInstance(project).findFile(vf) : null;
                if (!(psi instanceof QilletniFile)) continue;
                collectTopLevelFunctionDefs(list, psi);
            }
            if (contextFile.getVirtualFile() == null) {
                collectTopLevelFunctionDefs(list, contextFile);
            }
            return CachedValueProvider.Result.create(List.copyOf(list), PsiModificationTracker.MODIFICATION_COUNT, dev.qilletni.intellij.resolve.QilletniAliasResolver.rootsTracker());
        };
    }

    private static void collectTopLevelFunctionDefs(List<QilletniFunctionDef> list, PsiFile psi) {
        for (var def : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionDef.class)) {
            var onType = PsiTreeUtil.findChildOfType(def, QilletniFunctionOnType.class);
            if (onType != null) continue; // skip extension methods
            if (PsiTreeUtil.getParentOfType(def, QilletniEntityDef.class, false) != null) continue; // skip entity member functions
            list.add(def);
        }
    }

    // List all extension methods for a given receiver type visible from the given file
    // Note: This consults the StubIndex first using a composite key Receiver#name. Since completions
    // need the full list, we query per-file scope for ALL method names by scanning stubs via index keys
    // available in the files' stub trees. Behavior is constrained to files under 'qilletni-src'
    // directories (see QilletniImportUtil.filterToQilletniSrc).
    // To change this behavior (e.g., include libraries), adjust candidateFilesForType()
    // and/or QilletniImportUtil.filterToQilletniSrc.
    public static List<QilletniFunctionDef> listExtensionMethods(Project project, QilletniFile contextFile, String rawReceiverType) {
        System.out.println("[DEBUG_LOG][IndexFacade] listExtensionMethods receiverType=" + rawReceiverType + ", file=" + (contextFile != null ? contextFile.getVirtualFile() : null));
        Map<String, List<QilletniFunctionDef>> cache = CachedValuesManager.getManager(project).getCachedValue(contextFile, EXT_METHODS_BY_TYPE_CACHE_KEY, () ->
                CachedValueProvider.Result.create(new HashMap<>(), PsiModificationTracker.MODIFICATION_COUNT), false);

        // Unknown type: only suggest methods whose receiver is ANY
        if (rawReceiverType == null || rawReceiverType.isBlank()) {
            final String ANY = "ANY"; // Language rule: ANY-receiver methods apply to unknown types
            List<QilletniFunctionDef> cachedAny = cache.get(ANY);
            if (cachedAny != null) {
                List<QilletniFunctionDef> valid = new ArrayList<>();
                for (var f : cachedAny) if (f != null && f.isValid()) valid.add(f);
                if (!valid.isEmpty()) return valid;
            }
            var tnAny = new TypeName(null, ANY);
            List<VirtualFile> files = candidateFilesForType(contextFile, tnAny);
            System.out.println("[DEBUG_LOG][IndexFacade] ANY path candidate files=" + files);
            if (files.isEmpty() && contextFile.getVirtualFile() == null) {
                // scratch/unsaved: include current context PSI only
                List<QilletniFunctionDef> only = new ArrayList<>();
                collectExtensionMethodDefs(tnAny, only, contextFile);
                List<QilletniFunctionDef> imm = List.copyOf(only);
                cache.put(ANY, imm);
                return imm;
            }
            var scope = GlobalSearchScope.filesScope(project, new java.util.HashSet<>(files));
            System.out.println("[DEBUG_LOG][IndexFacade] ANY path scope size=" + files.size());
            // Consult the index using discovered function names within the constrained files.
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (VirtualFile vf : files) {
                var psi = vf != null ? PsiManager.getInstance(project).findFile(vf) : null;
                if (!(psi instanceof QilletniFile)) continue;
                for (var fn : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionName.class)) {
                    var id = fn.getId();
                    if (id != null) names.add(id.getText());
                }
            }
            List<QilletniFunctionDef> list = new ArrayList<>();
            System.out.println("[DEBUG_LOG][IndexFacade] ANY path function names enumerated=" + names);
            for (var nm : names) {
                String key = dev.qilletni.intellij.index.QilletniIndexConstants.extMethodKey(ANY, nm);
                if (key.isBlank()) continue;
                System.out.println("[DEBUG_LOG][IndexFacade] ANY path querying index key=" + key);
                var col = StubIndex.getElements(QilletniExtMethodIndex.KEY, key, project, scope, QilletniFunctionDef.class);
                int c=0; for (var def : col) { if (def != null && def.isValid()) { list.add(def); c++; } }
                System.out.println("[DEBUG_LOG][IndexFacade] ANY path index hits for key=" + key + " -> " + c);
            }
            List<QilletniFunctionDef> imm = List.copyOf(list);
            cache.put(ANY, imm);
            return imm;
        }

        List<QilletniFunctionDef> cached = cache.get(rawReceiverType);
        if (cached != null) {
            List<QilletniFunctionDef> valid = new ArrayList<>();
            for (var f : cached) if (f != null && f.isValid()) valid.add(f);
            if (!valid.isEmpty()) return valid;
        }

        var tn = parseTypeName(rawReceiverType);
        // We do not handle alias-qualified types here per requirement (focus on unqualified simple names)
        List<VirtualFile> files = candidateFilesForType(contextFile, tn);
        System.out.println("[DEBUG_LOG][IndexFacade] typed path candidate files for '" + tn.simple + "' = " + files);
        List<QilletniFunctionDef> list = new ArrayList<>();
        if (!files.isEmpty()) {
            // Consult the index by enumerating candidate function names from the constrained files,
            // then querying QilletniExtMethodIndex with composite key Receiver#Name within the same scope.
            var scope = GlobalSearchScope.filesScope(project, new java.util.HashSet<>(files));
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (VirtualFile vf : files) {
                var psi = vf != null ? PsiManager.getInstance(project).findFile(vf) : null;
                if (!(psi instanceof QilletniFile)) continue;
                for (var fn : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionName.class)) {
                    var id = fn.getId();
                    if (id != null) names.add(id.getText());
                }
            }
            System.out.println("[DEBUG_LOG][IndexFacade] typed path function names enumerated=" + names);
            for (var nm : names) {
                String key = dev.qilletni.intellij.index.QilletniIndexConstants.extMethodKey(tn.simple, nm);
                if (key.isBlank()) continue;
                System.out.println("[DEBUG_LOG][IndexFacade] typed path querying index key=" + key);
                var col = StubIndex.getElements(QilletniExtMethodIndex.KEY, key, project, scope, QilletniFunctionDef.class);
                int c=0; for (var def : col) { if (def != null && def.isValid()) { list.add(def); c++; } }
                System.out.println("[DEBUG_LOG][IndexFacade] typed path index hits for key=" + key + " -> " + c);
            }
        }
        if (contextFile.getVirtualFile() == null) {
            collectExtensionMethodDefs(tn, list, contextFile);
        }
        List<QilletniFunctionDef> valid = new ArrayList<>();
        for (var f : list) if (f != null && f.isValid()) valid.add(f);
        List<QilletniFunctionDef> immutable = List.copyOf(valid);
        cache.put(rawReceiverType, immutable);
        return immutable;
    }

    private static void collectExtensionMethodDefs(TypeName tn, List<QilletniFunctionDef> list, PsiFile psi) {
        for (var def : PsiTreeUtil.findChildrenOfType(psi, QilletniFunctionDef.class)) {
            var onType = PsiTreeUtil.findChildOfType(def, QilletniFunctionOnType.class);
            if (onType == null) continue;
            // Find the LAST ID under the on-type to get the simple type name (ignoring 'on' token and qualifiers)
            PsiElement id = null;
            for (PsiElement c = onType.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNode() != null) {
                    var t = c.getNode().getElementType();
                    if (t == QilletniTypes.ID
                            || t == QilletniTypes.ANY_TYPE || t == QilletniTypes.INT_TYPE || t == QilletniTypes.DOUBLE_TYPE
                            || t == QilletniTypes.STRING_TYPE || t == QilletniTypes.BOOLEAN_TYPE || t == QilletniTypes.COLLECTION_TYPE
                            || t == QilletniTypes.SONG_TYPE || t == QilletniTypes.ALBUM_TYPE || t == QilletniTypes.JAVA_TYPE) {
                        id = c;
                    }
                }
            }
            if (id == null) continue;
            if (!id.getText().contentEquals(tn.simple)) continue;
            list.add(def);
        }
    }

    private static List<VirtualFile> candidateFilesForType(QilletniFile contextFile, TypeName tn) {
        List<VirtualFile> res = new ArrayList<>();
        if (tn.qualifier != null) {
            // Qualified name: restrict strictly to files imported under this alias
            res.addAll(QilletniAliasResolver.getFilesForQualifier(contextFile, tn.qualifier));
        } else {
            // Unqualified: current file + ALL imported files (project + libraries + auto-imports)
            if (contextFile.getVirtualFile() != null) {
                res.add(contextFile.getVirtualFile());
            }
            // Include transitive imported files from both project and libraries so symbols are available unqualified
            res.addAll(QilletniAliasResolver.getAllImportedFiles(contextFile));
        }
        return res;
    }

    private static Map<String, QilletniEntityDef> getOrCreateEntityCache(Project project, QilletniFile file) {
        return CachedValuesManager.getManager(project).getCachedValue(file, ENTITY_CACHE_KEY, () ->
                CachedValueProvider.Result.create(new HashMap<>(), PsiModificationTracker.MODIFICATION_COUNT, dev.qilletni.intellij.resolve.QilletniAliasResolver.rootsTracker()), false);
    }

    private static Map<String, List<QilletniFunctionDef>> getOrCreateExtMethodsCache(Project project, QilletniFile file) {
        return CachedValuesManager.getManager(project).getCachedValue(file, EXT_METHODS_CACHE_KEY, () ->
                CachedValueProvider.Result.create(new HashMap<>(), PsiModificationTracker.MODIFICATION_COUNT, dev.qilletni.intellij.resolve.QilletniAliasResolver.rootsTracker()), false);
    }

    private static TypeName parseTypeName(String raw) {
        int dot = raw.indexOf('.');
        if (dot > 0 && dot < raw.length() - 1) {
            return new TypeName(raw.substring(0, dot), raw.substring(dot + 1));
        }
        return new TypeName(null, raw);
    }

    private static final class TypeName {
        final String qualifier;
        final String simple;
        TypeName(String qualifier, String simple) {
            this.qualifier = qualifier;
            this.simple = simple;
        }
    }
}
