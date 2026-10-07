package dev.qilletni.intellij.resolve;

import org.jetbrains.annotations.Nullable;

/**
 * A library import path such as {@code "std:path/in/lib.ql"}: the library name (without its scope) and the path
 * inside the library's {@code qilletni-src}.
 */
public record QilletniLibraryImportPath(String libraryName, String relativePath) {

    /** Whether the raw import string uses the {@code name:path} form rather than a project-local path. */
    public static boolean isLibraryImport(@Nullable String raw) {
        return raw != null && raw.indexOf(':') > 0;
    }

    /** Parses {@code name:path}; returns null unless both parts are non-blank. A leading '/' on the path is dropped. */
    public static @Nullable QilletniLibraryImportPath parse(@Nullable String raw) {
        if (raw == null) return null;
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon >= raw.length() - 1) return null;
        String libName = raw.substring(0, colon).trim();
        String relPath = raw.substring(colon + 1).trim();
        if (relPath.startsWith("/")) relPath = relPath.substring(1);
        if (libName.isEmpty() || relPath.isEmpty()) return null;
        return new QilletniLibraryImportPath(libName, relPath);
    }
}
