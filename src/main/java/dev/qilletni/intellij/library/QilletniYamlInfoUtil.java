package dev.qilletni.intellij.library;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import dev.qilletni.intellij.library.model.QilletniManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static dev.qilletni.intellij.util.QilletniConstants.MANIFEST_FILE;
import static dev.qilletni.intellij.util.QilletniConstants.QILLETNI_SRC;

/**
 * Reads {@code qilletni-src/qilletni_info.yml} of project sources, caching the parsed manifest per file.
 */
public final class QilletniYamlInfoUtil {
    private static final Logger LOG = Logger.getInstance(QilletniYamlInfoUtil.class);

    private record CachedManifest(long modificationStamp, @Nullable QilletniManifest manifest) {}

    private static final Key<CachedManifest> MANIFEST_KEY = Key.create("qilletni.manifest.cached");

    private QilletniYamlInfoUtil() {}

    /**
     * If the given file (usually a .ql file) is inside a qilletni-src tree, returns that root.
     */
    public static @Nullable VirtualFile findQilletniSrcRoot(@Nullable VirtualFile vf) {
        for (VirtualFile cur = vf; cur != null; cur = cur.getParent()) {
            if (QILLETNI_SRC.equals(cur.getName())) return cur;
        }
        return null;
    }

    /** The {@code qilletni-src} directories, containing a manifest, directly under each module content root. */
    public static @NotNull List<VirtualFile> findProjectQilletniSrcRoots(@NotNull Project project) {
        List<VirtualFile> roots = new ArrayList<>();
        for (var module : ModuleManager.getInstance(project).getModules()) {
            for (var root : ModuleRootManager.getInstance(module).getContentRoots()) {
                var candidate = root.findChild(QILLETNI_SRC);
                if (candidate == null || !candidate.isValid()) continue;
                var yaml = candidate.findChild(MANIFEST_FILE);
                if (yaml != null && yaml.isValid()) roots.add(candidate);
            }
        }
        return roots;
    }

    /**
     * The parsed manifest under the given qilletni-src root, or null if it is missing or invalid.
     * The result is cached on the manifest file until its content changes.
     */
    public static @Nullable QilletniManifest readManifest(@NotNull VirtualFile qilletniSrcRoot) {
        VirtualFile yaml = qilletniSrcRoot.findChild(MANIFEST_FILE);
        if (yaml == null || !yaml.isValid()) return null;
        long stamp = yaml.getModificationStamp();
        var cached = yaml.getUserData(MANIFEST_KEY);
        if (cached != null && cached.modificationStamp() == stamp) return cached.manifest();

        QilletniManifest manifest;
        try {
            manifest = QilletniManifest.parse(VfsUtilCore.loadText(yaml));
        } catch (IOException e) {
            LOG.debug("Failed to read " + yaml.getPath(), e);
            manifest = null;
        }
        yaml.putUserData(MANIFEST_KEY, new CachedManifest(stamp, manifest));
        return manifest;
    }

    /**
     * Reads native_classes from qilletni_info.yml under the provided qilletni-src root.
     * Returns empty list if file is missing or invalid.
     */
    public static @NotNull List<String> readNativeClassesFromYaml(@NotNull Project project, @NotNull VirtualFile qilletniSrcRoot) {
        if (DumbService.isDumb(project)) return List.of();
        var manifest = readManifest(qilletniSrcRoot);
        return manifest != null ? manifest.nativeClasses() : List.of();
    }
}
