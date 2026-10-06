package dev.qilletni.intellij.resolve.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import dev.qilletni.intellij.library.QilletniYamlInfoUtil;
import dev.qilletni.intellij.resolve.QilletniLibraryPathResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        if (rawImportPath == null) return false;
        int colon = rawImportPath.indexOf(':');
        return colon > 0 && colon < rawImportPath.length() - 1;
    }

    @Override
    public List<VirtualFile> resolve(Project project, String rawImportPath, VirtualFile contextFile) {
        if (project == null || rawImportPath == null || contextFile == null) return List.of();
        // Only allow when context is under <project>/examples
        VirtualFile projectRoot = findProjectRoot(project);
        if (projectRoot == null) return List.of();
        var examples = projectRoot.findChild("examples");
        if (examples == null || !examples.isDirectory()) return List.of();
        if (!VfsUtilCore.isAncestor(examples, contextFile, true)) {
            return List.of();
        }

        int colon = rawImportPath.indexOf(':');
        if (colon <= 0 || colon >= rawImportPath.length() - 1) return List.of();
        String libName = rawImportPath.substring(0, colon).trim();
        String relPath = rawImportPath.substring(colon + 1).trim();
        if (relPath.startsWith("/")) relPath = relPath.substring(1);
        if (libName.isEmpty() || relPath.isEmpty()) return List.of();

        // Disallow relative pathing beyond root
        if (relPath.startsWith("./") || relPath.contains("/./") || relPath.contains("..")) return List.of();

        // Resolve qilletni-src root and validate library name from YAML
        var qlSrc = projectRoot.findChild("qilletni-src");
        if (qlSrc == null || !qlSrc.isDirectory()) return List.of();

        String yamlName = readLibraryNameFromYaml(project, qlSrc);
        if (yamlName == null || yamlName.isBlank()) return List.of();
        if (!yamlName.equalsIgnoreCase(libName)) return List.of();

        var target = VfsUtilCore.findRelativeFile(relPath, qlSrc);
        if (target == null || !target.exists() || target.isDirectory()) return List.of();

        // Basic symlink guard: if canonical path differs (and is known), skip
        try {
            String canonical = target.getCanonicalPath();
            if (canonical != null && !canonical.equals(target.getPath())) return List.of();
        } catch (Throwable ignored) {
        }

        Set<VirtualFile> out = new LinkedHashSet<>();
        out.add(target);
        return new ArrayList<>(out);
    }

    private static VirtualFile findProjectRoot(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        File io = new File(basePath);
        return LocalFileSystem.getInstance().findFileByIoFile(io);
    }

    private static String readLibraryNameFromYaml(Project project, VirtualFile qilletniSrcRoot) {
        // Use existing util to load the map via SnakeYAML with minimal duplication
        // We don't have a direct API for name; re-read similar to util's pattern
        var yaml = VfsUtilCore.findRelativeFile("qilletni_info.yml", qilletniSrcRoot);
        if (yaml == null || !yaml.isValid()) return null;
        try (var is = yaml.getInputStream(); var reader = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)) {
            var yamlLoader = new org.yaml.snakeyaml.Yaml();
            Object data = yamlLoader.load(reader);
            if (data instanceof java.util.Map<?, ?> map) {
                Object name = map.get("name");
                if (name instanceof String s && !s.isBlank()) {
                    var trimmed = s.trim();
                    // Remove scope from name if present
                    if (trimmed.contains("/")) {
                        trimmed = trimmed.substring(trimmed.lastIndexOf('/') + 1);
                    }
                    return trimmed;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
