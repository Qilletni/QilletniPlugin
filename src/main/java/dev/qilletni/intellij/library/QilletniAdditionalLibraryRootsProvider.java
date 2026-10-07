package dev.qilletni.intellij.library;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Exposes the Qilletni packages the project depends on (see {@link QilletniProjectDependencies}) as External
 * Libraries: {@code qilletni-src} as sources and {@code native.jar} as classes, so both are indexed and navigable.
 */
public final class QilletniAdditionalLibraryRootsProvider extends AdditionalLibraryRootsProvider {

    @Override
    public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
        List<SyntheticLibrary> result = new ArrayList<>();
        for (var lib : QilletniProjectDependencies.getInstance(project).getState().allLibraries()) {
            List<VirtualFile> sources = new ArrayList<>();
            List<VirtualFile> binaries = new ArrayList<>();

            var src = lib.srcRoot();
            if (src != null && src.isValid()) sources.add(src);

            var nativeJar = lib.nativeJarRoot();
            if (nativeJar != null && nativeJar.isValid()) binaries.add(nativeJar);

            if (sources.isEmpty() && binaries.isEmpty()) continue;
            var id = lib.scope() != null ? lib.scope() + "/" + lib.name() : lib.name();
            var presentable = id + (lib.version() != null ? " " + lib.version() : "");
            result.add(new QilletniBinarySyntheticLibrary(lib.name(), presentable, binaries, sources));
        }
        return result;
    }

    @Override
    public @NotNull Collection<VirtualFile> getRootsToWatch(@NotNull Project project) {
        var roots = new ArrayList<VirtualFile>();
        for (var lib : QilletniProjectDependencies.getInstance(project).getState().allLibraries()) {
            if (lib.srcRoot() != null && lib.srcRoot().isValid()) roots.add(lib.srcRoot());
        }
        return roots;
    }
}
