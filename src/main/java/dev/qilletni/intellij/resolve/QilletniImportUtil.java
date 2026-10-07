package dev.qilletni.intellij.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.psi.QilletniImportFile;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.resolve.impl.LibraryRootsPathResolver;
import dev.qilletni.intellij.resolve.impl.SelfLibraryExamplesOnlyResolver;

import java.io.File;
import java.util.*;

import static dev.qilletni.intellij.util.QilletniConstants.EXAMPLES;
import static dev.qilletni.intellij.util.QilletniConstants.QILLETNI_SRC;

/**
 * Utilities to collect import information for a Qilletni file and resolve project-local imported files.
 * - Supports imports like: import "directFile.ql" [AS alias]
 * - Library imports like: import "libName:path/in/lib.ql", resolved through {@link QilletniLibraryPathResolver}s.
 */
final class QilletniImportUtil {
    private QilletniImportUtil() {}

    /**
     * Computes the transitive closure of all imported files (aliased and unaliased, including libraries if resolved).
     * Alias-qualified names are re-exported transitively.
     */
    static List<VirtualFile> getTransitiveAllImportedFiles(QilletniFile contextFile) {
        return traverseTransitive(contextFile, /*projectLocalOnly*/ false, /*qualifierFilter*/ null);
    }

    /**
     * Returns transitive files reachable under the given alias qualifier. This re-exports alias-qualified names
     * through nested imports (recursive), per language decision. Project-local filtering is applied so completions
     * and resolution respect source roots boundaries.
     */
    static List<VirtualFile> getTransitiveFilesForQualifier(QilletniFile contextFile, String qualifier) {
        return traverseTransitive(contextFile, /*projectLocalOnly*/ true, qualifier);
    }

    private static List<VirtualFile> traverseTransitive(QilletniFile startFile, boolean projectLocalOnly, String qualifierFilter) {
        if (startFile == null) return List.of();
        // Seed frontier depending on qualifier filter
        Map<String, List<VirtualFile>> directAliased = collectAliasedImports(startFile);
        List<VirtualFile> directUnaliased = collectUnaliasedImports(startFile);
        Deque<VirtualFile> frontier = new ArrayDeque<>();
        LinkedHashSet<VirtualFile> visited = new LinkedHashSet<>();
        var psiManager = PsiManager.getInstance(startFile.getProject());

        if (qualifierFilter != null) {
            frontier.addAll(directAliased.getOrDefault(qualifierFilter, List.of()));
        } else {
            frontier.addAll(directUnaliased);
            for (var list : directAliased.values()) frontier.addAll(list);
        }

        while (!frontier.isEmpty()) {
            var vf = frontier.removeFirst();
            if (vf == null || !visited.add(vf)) continue;
            // Load PSI to read its direct imports
            if (!(psiManager.findFile(vf) instanceof QilletniFile qf)) continue;
            // Collect that file's direct imports
            Map<String, List<VirtualFile>> childAliased = collectAliasedImports(qf);
            List<VirtualFile> childUnaliased = collectUnaliasedImports(qf);
            // Apply project-local filter if requested
            List<VirtualFile> next = new ArrayList<>();
            if (qualifierFilter != null) {
                next.addAll(childAliased.getOrDefault(qualifierFilter, List.of()));
            } else {
                next.addAll(childUnaliased);
                for (var list : childAliased.values()) next.addAll(list);
            }
            if (projectLocalOnly) {
                next = filterToQilletniSrc(startFile, next);
            }
            for (var n : next) if (n != null && !visited.contains(n)) frontier.addLast(n);
        }

        List<VirtualFile> result = new ArrayList<>(visited);
        if (projectLocalOnly) {
            // Ensure all results adhere to project-local constraints (seed may have included library files)
            result = new ArrayList<>(filterToQilletniSrc(startFile, result));
        }
        return result;
    }

    // Resolver chain for library-style imports (e.g., "libName:path/in/lib.ql").
    private static final List<QilletniLibraryPathResolver> LIB_RESOLVERS = List.of(
            new SelfLibraryExamplesOnlyResolver(),
            new LibraryRootsPathResolver()
    );

    // Exposed for alias resolver
    static Map<String, List<VirtualFile>> collectAliasedImports(QilletniFile file) {
        Map<String, List<VirtualFile>> res = new LinkedHashMap<>();
        Project project = file.getProject();
        var contextVf = file.getVirtualFile();
        for (var imp : PsiTreeUtil.findChildrenOfType(file, QilletniImportFile.class)) {
            String raw = findStringLiteralText(imp);
            if (raw == null) continue;
            String alias = findAlias(imp);
            if (alias == null) continue; // this method only collects aliased imports

            if (QilletniLibraryImportPath.isLibraryImport(raw)) {
                List<VirtualFile> files = resolveLibraryFiles(project, raw, contextVf);
                if (!files.isEmpty()) {
                    res.computeIfAbsent(alias, k -> new ArrayList<>()).addAll(files);
                }
            } else {
                var vf = resolveProjectLocalFile(file, raw);
                if (vf != null) {
                    res.computeIfAbsent(alias, k -> new ArrayList<>()).add(vf);
                }
            }
        }
        return res;
    }

    private static List<VirtualFile> collectUnaliasedImports(QilletniFile file) {
        List<VirtualFile> res = new ArrayList<>();
        Project project = file.getProject();
        var contextVf = file.getVirtualFile();
        for (var imp : PsiTreeUtil.findChildrenOfType(file, QilletniImportFile.class)) {
            String raw = findStringLiteralText(imp);
            if (raw == null) continue;
            String alias = findAlias(imp);
            if (alias != null) continue; // skip aliased; this method collects only unaliased
            if (QilletniLibraryImportPath.isLibraryImport(raw)) {
                res.addAll(resolveLibraryFiles(project, raw, contextVf));
            } else {
                var vf = resolveProjectLocalFile(file, raw);
                if (vf != null) res.add(vf);
            }
        }
        // Auto-imports of the libraries this file's project depends on
        res.addAll(collectAutoImports(project, contextVf));
        return res;
    }

    private static List<VirtualFile> collectAutoImports(Project project, VirtualFile contextVf) {
        var out = new LinkedHashSet<VirtualFile>();
        for (var lib : QilletniProjectDependencies.getInstance(project).librariesFor(contextVf)) {
            var root = lib.srcRoot();
            if (root == null || !root.isValid()) continue;
            for (var rel : lib.autoImportFiles()) {
                if (rel == null || rel.isBlank()) continue;
                var vf = VfsUtilCore.findRelativeFile(rel.startsWith("/") ? rel.substring(1) : rel, root);
                if (vf != null) out.add(vf);
            }
        }
        return new ArrayList<>(out);
    }

    private static String findStringLiteralText(QilletniImportFile imp) {
        // STRING token holds the path, includes quotes; strip them
        for (PsiElement c = imp.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode() != null && c.getNode().getElementType() == QilletniTypes.STRING) {
                String t = c.getText();
                return t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"") ? t.substring(1, t.length() - 1) : t;
            }
        }
        return null;
    }

    private static String findAlias(QilletniImportFile imp) {
        for (PsiElement c = imp.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode() != null && c.getNode().getElementType() == QilletniTypes.ID) {
                return c.getText();
            }
        }
        return null;
    }

    /** Asks each resolver in priority order and returns the first non-empty result. */
    private static List<VirtualFile> resolveLibraryFiles(Project project, String raw, VirtualFile contextFile) {
        if (project == null || raw == null) return List.of();
        for (var resolver : LIB_RESOLVERS) {
            if (!resolver.supports(raw)) continue;
            List<VirtualFile> files = resolver.resolve(project, raw, contextFile);
            if (files != null && !files.isEmpty()) return files;
        }
        return List.of();
    }

    private static VirtualFile resolveProjectLocalFile(QilletniFile baseFile, String importPathLiteral) {
        // Resolve project-local file paths relative to the importing file's directory.
        // Supports ./ and ../ segments naturally via VirtualFile.findFileByRelativePath.
        // Fallback: project base path to maintain backwards compatibility for root-based imports.
        if (QilletniLibraryImportPath.isLibraryImport(importPathLiteral)) return null; // library paths handled elsewhere
        if (baseFile == null) return null;
        var vf = baseFile.getVirtualFile();
        var baseDir = vf != null ? vf.getParent() : null;
        if (baseDir != null) {
            var rel = baseDir.findFileByRelativePath(importPathLiteral);
            if (rel != null) return rel;
        }
        String basePath = baseFile.getProject().getBasePath();
        return basePath != null ? LocalFileSystem.getInstance().findFileByIoFile(new File(basePath, importPathLiteral)) : null;
    }

    private static List<VirtualFile> filterToQilletniSrc(QilletniFile contextFile, List<VirtualFile> files) {
        // Context-aware project-local filter. Roots are found by directory name, so 'qilletni-src' does not
        // need to be marked as a Sources Root (build-system re-imports would undo that anyway).
        // - If context is under 'qilletni-src': allow only files under a 'qilletni-src' dir of the same module's content roots.
        // - If context is under 'examples': allow only files under the SAME 'examples' dir as the context file.
        // - Otherwise: allow only files in the same directory as the context file (same parent VirtualFile).
        if (files == null || files.isEmpty()) return List.of();
        var project = contextFile.getProject();
        var fileIndex = ProjectFileIndex.getInstance(project);
        var ctxVf = contextFile.getVirtualFile();
        var ctxRoot = ctxVf != null ? findQilletniRoot(ctxVf, fileIndex) : null;
        var ctxParent = ctxVf != null ? ctxVf.getParent() : null;

        // Build allowed root URLs per context
        Set<String> allowedRootUrls = new HashSet<>();
        String mode = "same-dir"; // "qsrc", "examples", or "same-dir"
        if (ctxRoot != null) {
            if (QILLETNI_SRC.equals(ctxRoot.getName())) {
                mode = "qsrc";
                allowedRootUrls.add(ctxRoot.getUrl());
                Module module = ModuleUtilCore.findModuleForFile(ctxVf, project);
                if (module != null) {
                    for (var contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
                        var qsrc = contentRoot.findChild(QILLETNI_SRC);
                        if (qsrc != null && qsrc.isDirectory()) allowedRootUrls.add(qsrc.getUrl());
                    }
                }
            } else {
                mode = "examples";
                allowedRootUrls.add(ctxRoot.getUrl());
            }
        }

        List<VirtualFile> result = new ArrayList<>();
        for (var vf : files) {
            if (vf == null) continue;
            // Files outside the project content came from a library import; the source-root rules don't apply
            if (!fileIndex.isInContent(vf)) {
                result.add(vf);
                continue;
            }
            if ("same-dir".equals(mode)) {
                if (ctxParent != null && vf.getParent() != null && ctxParent.equals(vf.getParent())) {
                    result.add(vf);
                }
                continue;
            }

            // For mode qsrc/examples, the candidate must sit under one of the allowed roots
            var root = findQilletniRoot(vf, fileIndex);
            if (root != null && root.getName().equals(ctxRoot.getName()) && allowedRootUrls.contains(root.getUrl())) {
                result.add(vf);
            }
        }
        return result;
    }

    /**
     * Returns the nearest ancestor directory named 'qilletni-src' or 'examples', without walking above the
     * file's content root (so an unrelated 'examples' directory outside the project is never matched).
     */
    private static VirtualFile findQilletniRoot(VirtualFile vf, ProjectFileIndex fileIndex) {
        var contentRoot = fileIndex.getContentRootForFile(vf);
        for (var cur = vf.getParent(); cur != null; cur = cur.getParent()) {
            var name = cur.getName();
            if (QILLETNI_SRC.equals(name) || EXAMPLES.equals(name)) return cur;
            if (cur.equals(contentRoot)) break;
        }
        return null;
    }
}
