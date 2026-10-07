package dev.qilletni.intellij.library;

import com.intellij.openapi.vfs.VirtualFile;
import dev.qilletni.intellij.library.model.InstalledQllLibrary;
import dev.qilletni.intellij.library.model.QilletniManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A Qilletni project (a directory containing {@code qilletni-src/qilletni_info.yml}) and its resolved dependencies.
 *
 * @param rootDir  the project directory, parent of {@code qilletni-src}
 * @param srcRoot  the {@code qilletni-src} directory
 * @param manifest the parsed manifest, or {@code null} if it could not be parsed
 */
public record QilletniProjectModel(
        @NotNull VirtualFile rootDir,
        @NotNull VirtualFile srcRoot,
        @Nullable QilletniManifest manifest,
        @NotNull Status status,
        @NotNull List<QilletniDependency> dependencies
) {
    public enum Status {
        /** Every locked package is installed. */
        OK,
        /** There is no {@code qilletni.lock}; dependencies were matched against installed packages. */
        NO_LOCKFILE,
        /** {@code qilletni_info.yml} declares dependencies the lockfile doesn't contain. */
        LOCK_STALE,
        /** Some packages are not installed. */
        MISSING_PACKAGES,
        /** {@code qilletni_info.yml} could not be parsed. */
        INVALID_MANIFEST;

        /** Whether running {@code qpm install} would fix this status. */
        public boolean needsInstall() {
            return this == NO_LOCKFILE || this == LOCK_STALE || this == MISSING_PACKAGES;
        }
    }

    public @NotNull String displayName() {
        return manifest != null && manifest.name() != null ? manifest.name() : rootDir.getName();
    }

    public @NotNull List<InstalledQllLibrary> libraries() {
        return dependencies.stream().map(QilletniDependency::library).filter(Objects::nonNull).toList();
    }

    public @NotNull List<QilletniDependency> missing() {
        return dependencies.stream().filter(QilletniDependency::isMissing).toList();
    }
}
