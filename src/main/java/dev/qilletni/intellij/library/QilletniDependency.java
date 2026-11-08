package dev.qilletni.intellij.library;

import dev.qilletni.intellij.library.model.InstalledQllLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A package a Qilletni project depends on, and what it resolved to on this machine.
 *
 * @param id             the package id, {@code scope/name}
 * @param requestedRange the range from {@code qilletni_info.yml}, or {@code null} for a transitive dependency
 * @param version        the locked or chosen version, if known
 * @param archive        the resolved .qll archive, or {@code null} if it is not installed
 * @param library        the loaded library, or {@code null} if it is missing or failed to load
 * @param problem        a user-facing description of what is wrong, or {@code null}
 */
public record QilletniDependency(
        @NotNull String id,
        @Nullable String requestedRange,
        @Nullable String version,
        @Nullable Path archive,
        @Nullable InstalledQllLibrary library,
        @Nullable String problem
) {
    public boolean isDirect() {
        return requestedRange != null;
    }

    public boolean isMissing() {
        return library == null;
    }
}
