package dev.qilletni.intellij.library.model;

import java.util.List;

/**
 * Metadata model for a Qilletni library contained in a .qll archive.
 * Mirrors the qll.info JSON structure. Versions are kept as raw strings (e.g. {@code 1.0.0},
 * {@code 1.0.0-SNAPSHOT}, or a range such as {@code ^1.0.0} for dependencies); use {@link QllVersion} to compare them.
 */
public record QllInfo(
        String scope,
        String name,
        String version,
        String author,
        String description,
        String sourceUrl,
        List<Dependency> dependencies,
        String providerClass,
        String nativeBindFactoryClass,
        List<String> nativeClasses,
        List<String> autoImportFiles
) {
    public record Dependency(
            String scope,
            String name,
            String version
    ) {}
}
