package dev.qilletni.intellij.library.model;

import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.util.List;

/**
 * Snapshot of an installed Qilletni library discovered from a .qll archive.
 */
public record InstalledQllLibrary(
        String scope,
        String name,
        String version,
        String author,
        String description,
        Path archivePath,
        VirtualFile srcRoot,
        VirtualFile nativeJarRoot,
        List<String> nativeClasses,
        List<String> autoImportFiles
) {
}
