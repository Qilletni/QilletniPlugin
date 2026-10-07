package dev.qilletni.intellij.resolve.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import dev.qilletni.intellij.library.QilletniYamlInfoUtil;
import dev.qilletni.intellij.resolve.QilletniLibraryImportPath;
import dev.qilletni.intellij.resolve.QilletniLibraryPathResolver;

import java.io.File;
import java.util.List;

import static dev.qilletni.intellij.util.QilletniConstants.EXAMPLES;
import static dev.qilletni.intellij.util.QilletniConstants.QILLETNI_SRC;

/**
 * Resolves library imports that target the current project as a library, but only when the importing
 * file lives under the top-level 'examples' directory of the project. This resolver takes priority
 * over other resolvers in QilletniImportUtil.
 *
 * Constraints:
 * - No relative path segments are supported ("..", ".", "./", etc.).
 * - Symlinks are not supported (basic guard using canonical vs path mismatch).
 */
public final class SelfLibraryExamplesOnlyResolver implements QilletniLibraryPathResolver {
    @Override
    public boolean supports(String rawImportPath) {
        return QilletniLibraryImportPath.parse(rawImportPath) != null;
    }

    @Override
    public List<VirtualFile> resolve(Project project, String rawImportPath, VirtualFile contextFile) {
        if (project == null || rawImportPath == null || contextFile == null) return List.of();
        // Only allow when context is under <project>/examples
        VirtualFile projectRoot = findProjectRoot(project);
        if (projectRoot == null) return List.of();
        var examples = projectRoot.findChild(EXAMPLES);
        if (examples == null || !examples.isDirectory()) return List.of();
        if (!VfsUtilCore.isAncestor(examples, contextFile, true)) return List.of();

        var importPath = QilletniLibraryImportPath.parse(rawImportPath);
        if (importPath == null) return List.of();
        String relPath = importPath.relativePath();

        // Disallow relative pathing beyond root
        if (relPath.startsWith("./") || relPath.contains("/./") || relPath.contains("..")) return List.of();

        // Resolve qilletni-src root and validate library name from the manifest
        var qlSrc = projectRoot.findChild(QILLETNI_SRC);
        if (qlSrc == null || !qlSrc.isDirectory()) return List.of();

        var manifest = QilletniYamlInfoUtil.readManifest(qlSrc);
        String yamlName = manifest != null ? manifest.simpleName() : null;
        if (yamlName == null || !yamlName.equalsIgnoreCase(importPath.libraryName())) return List.of();

        var target = VfsUtilCore.findRelativeFile(relPath, qlSrc);
        if (target == null || !target.exists() || target.isDirectory()) return List.of();

        // Basic symlink guard: if canonical path differs (and is known), skip
        String canonical = target.getCanonicalPath();
        if (canonical != null && !canonical.equals(target.getPath())) return List.of();

        return List.of(target);
    }

    private static VirtualFile findProjectRoot(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        return LocalFileSystem.getInstance().findFileByIoFile(new File(basePath));
    }
}
