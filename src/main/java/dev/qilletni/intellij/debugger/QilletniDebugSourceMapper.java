package dev.qilletni.intellij.debugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.model.InstalledQllLibrary;
import org.eclipse.lsp4j.debug.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Maps between IDE files and the adapter's {@link Source#getPath()}:
 * <ul>
 *   <li>files on disk are absolute real paths;</li>
 *   <li>files inside an installed {@code .qll} are {@code qll://<package>/<path under qilletni-src>}, which this side
 *       resolves to the IDE's extracted copy of that library.</li>
 * </ul>
 */
public final class QilletniDebugSourceMapper {
    public static final String QLL_SCHEME = "qll://";

    private final Supplier<List<InstalledQllLibrary>> libraries;

    public QilletniDebugSourceMapper(@NotNull Supplier<List<InstalledQllLibrary>> libraries) {
        this.libraries = libraries;
    }

    /** Libraries of the program's own project first, so its locked versions win over other projects' versions. */
    public static @NotNull QilletniDebugSourceMapper forProgram(@NotNull Project project, @Nullable VirtualFile target) {
        return new QilletniDebugSourceMapper(() -> {
            var deps = QilletniProjectDependencies.getInstance(project);
            var all = new LinkedHashSet<>(deps.librariesFor(target));
            all.addAll(deps.getState().allLibraries());
            return new ArrayList<>(all);
        });
    }

    /** The path the adapter knows {@code file} by, or null if it can't be part of a Qilletni program. */
    public @Nullable String toDapPath(@NotNull VirtualFile file) {
        for (var library : libraries.get()) {
            var root = library.srcRoot();
            if (root != null && VfsUtilCore.isAncestor(root, file, true)) {
                return QLL_SCHEME + library.name() + "/" + VfsUtilCore.getRelativePath(file, root, '/');
            }
        }
        if (!file.isInLocalFileSystem()) return null;
        var path = file.toNioPath();
        try {
            return path.toRealPath().toString();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    public @Nullable VirtualFile toFile(@Nullable Source source) {
        return source == null ? null : toFile(source.getPath());
    }

    public @Nullable VirtualFile toFile(@Nullable String dapPath) {
        if (dapPath == null || dapPath.isEmpty()) return null;
        if (dapPath.startsWith(QLL_SCHEME)) {
            var rest = dapPath.substring(QLL_SCHEME.length());
            int slash = rest.indexOf('/');
            if (slash <= 0) return null;
            var name = rest.substring(0, slash);
            var relative = rest.substring(slash + 1);
            for (var library : libraries.get()) {
                if (name.equals(library.name()) && library.srcRoot() != null) {
                    var file = library.srcRoot().findFileByRelativePath(relative);
                    if (file != null) return file;
                }
            }
            return null;
        }
        try {
            return LocalFileSystem.getInstance().findFileByNioFile(Path.of(dapPath));
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
