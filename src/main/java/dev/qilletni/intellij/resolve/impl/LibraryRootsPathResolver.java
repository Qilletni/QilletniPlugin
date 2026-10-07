package dev.qilletni.intellij.resolve.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.resolve.QilletniLibraryPathResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves library imports such as {@code import "std:path/in/lib.ql"} against the packages the importing file's
 * project depends on. The part before the colon is the package name without its scope.
 */
public final class LibraryRootsPathResolver implements QilletniLibraryPathResolver {
    @Override
    public boolean supports(String rawImportPath) {
        if (rawImportPath == null) return false;
        int colon = rawImportPath.indexOf(':');
        return colon > 0 && colon < rawImportPath.length() - 1;
    }

    @Override
    public List<VirtualFile> resolve(Project project, String rawImportPath, VirtualFile contextFile) {
        if (project == null || rawImportPath == null) return List.of();
        int colon = rawImportPath.indexOf(':');
        if (colon <= 0 || colon >= rawImportPath.length() - 1) return List.of();

        String libName = rawImportPath.substring(0, colon).trim();
        String relPath = rawImportPath.substring(colon + 1).trim();
        if (relPath.startsWith("/")) relPath = relPath.substring(1);
        if (libName.isEmpty() || relPath.isEmpty()) return List.of();

        List<VirtualFile> matches = new ArrayList<>();
        for (var lib : QilletniProjectDependencies.getInstance(project).librariesFor(contextFile)) {
            var src = lib.srcRoot();
            if (src == null || !src.isValid() || !libName.equalsIgnoreCase(lib.name())) continue;
            var target = VfsUtilCore.findRelativeFile(relPath, src);
            if (target != null && !target.isDirectory()) matches.add(target);
        }
        return matches;
    }
}
