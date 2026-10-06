package dev.qilletni.intellij.library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.qilletni.intellij.library.model.InstalledQllLibrary;
import dev.qilletni.intellij.library.model.QllInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Application-level manager that discovers .qll archives and exposes their sources and metadata.
 * Expects packages in hierarchical structure: <scope>/<name>/<version>.qll
 */
@Service
public final class QilletniLibraryManager {

    public record LibraryConflict(String name, List<Path> archives) {}
    public record LibraryLoadError(Path archive, String message) {}

    private static final String NOTIFICATION_GROUP = "Qilletni Libraries";

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(List.of(), List.of(), List.of(), System.currentTimeMillis()));
    private final Gson gson = new Gson();

    private record Snapshot(List<InstalledQllLibrary> installed,
                            List<LibraryConflict> conflicts,
                            List<LibraryLoadError> errors,
                            long updatedAt) {}

    /** Snapshot last update time in millis. */
    public long getLastUpdatedAt() { return snapshot.get().updatedAt; }

    public static QilletniLibraryManager getInstance() {
        return ApplicationManager.getApplication().getService(QilletniLibraryManager.class);
    }

    /** Returns current successfully installed libraries (conflict-free). */
    public List<InstalledQllLibrary> getInstalled() {
        return snapshot.get().installed;
    }

    public List<LibraryConflict> getConflicts() { return snapshot.get().conflicts; }
    public List<LibraryLoadError> getErrors() { return snapshot.get().errors; }

    /** Returns resolved VirtualFiles for all auto-import entries across installed libraries. */
    public List<VirtualFile> getAllAutoImportFiles() {
        var out = new LinkedHashSet<VirtualFile>();
        for (var lib : snapshot.get().installed) {
            var root = lib.srcRoot();
            if (root == null) continue;
            var list = lib.autoImportFiles();
            if (list == null) continue;
            for (var rel : list) {
                if (rel == null || rel.isBlank()) continue;
                var vf = VfsUtilCore.findRelativeFile(rel.startsWith("/") ? rel.substring(1) : rel, root);
                if (vf != null) out.add(vf);
            }
        }
        return new ArrayList<>(out);
    }

    /** Triggers a rescan of the libraries root (runs on pooled thread). */
    public void rescan() {
        AppExecutorUtil.getAppExecutorService().execute(this::doScan);
    }

    private void doScan() {
        var settings = QilletniLibrariesSettings.getInstance();
        var rootPathStr = settings != null ? settings.getLibrariesRootPath() : "";
        if (rootPathStr == null || rootPathStr.isBlank()) {
            snapshot.set(new Snapshot(List.of(), List.of(), List.of(), System.currentTimeMillis()));
            // No libraries root configured: still sync so previously mirrored libraries are removed
            dev.qilletni.intellij.library.QilletniProjectLibraries.requestSyncAllOpenProjects();
            return;
        }
        Path root = Path.of(rootPathStr);
        if (!Files.isDirectory(root)) {
            snapshot.set(new Snapshot(List.of(), List.of(), List.of(new LibraryLoadError(root, "Libraries root is not a directory")), System.currentTimeMillis()));
            notify("Qilletni libraries root is invalid: " + root, NotificationType.WARNING);
            return;
        }

        // Ensure VFS up-to-date before reading JAR contents
        VirtualFileManager.getInstance().asyncRefresh(null);

        List<ScanItem> scanned = new ArrayList<>();
        try {
            // Scan hierarchical structure: ~/.qilletni/packages/<scope>/<name>/<version>.qll
            try (var scopeDirs = Files.list(root)) {
                scopeDirs.filter(Files::isDirectory).forEach(scopeDir -> {
                    try (var nameDirs = Files.list(scopeDir)) {
                        nameDirs.filter(Files::isDirectory).forEach(nameDir -> {
                            try (var versionFiles = Files.list(nameDir)) {
                                versionFiles.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".qll"))
                                        .forEach(p -> {
                                            var item = scanArchive(p);
                                            if (item != null) {
                                                scanned.add(item);
                                            }
                                        });
                            } catch (IOException ignored) {}
                        });
                    } catch (IOException ignored) {}
                });
            }
        } catch (IOException ignored) {}

        // Group by name and resolve conflicts
        Map<String, List<ScanItem>> byName = scanned.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.installed != null && s.name != null && !s.name.isBlank())
                .collect(Collectors.groupingBy(s -> s.name));
        List<InstalledQllLibrary> installed = new ArrayList<>();
        List<LibraryConflict> conflicts = new ArrayList<>();
        for (var e : byName.entrySet()) {
            var name = e.getKey();
            var items = e.getValue();
            if (items.size() > 1) {
                conflicts.add(new LibraryConflict(name, items.stream().map(s -> s.archive).toList()));
                continue; // exclude all conflicting entries
            }
            var only = items.getFirst();
            installed.add(only.installed());
        }

        List<LibraryLoadError> errors = scanned.stream().flatMap(s -> s.errors.stream()).toList();
        snapshot.set(new Snapshot(List.copyOf(installed), List.copyOf(conflicts), List.copyOf(errors), System.currentTimeMillis()));

        if (!conflicts.isEmpty()) {
            var names = conflicts.stream().map(LibraryConflict::name)
                    .filter(n -> n != null && !n.isBlank() && !"<unknown>".equals(n))
                    .collect(Collectors.toList());
            if (!names.isEmpty()) {
                notify("Qilletni library name conflicts detected: " + String.join(", ", names), NotificationType.ERROR);
            } else {
                notify("Qilletni library name conflicts detected.", NotificationType.ERROR);
            }
        }

        // Update External Libraries presentation in all open projects (debounced on EDT)
        try {
            dev.qilletni.intellij.library.QilletniProjectLibraries.requestSyncAllOpenProjects();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Notify project-level components (e.g., AdditionalLibraryRootsProvider consumers) by refreshing VFS
        VirtualFileManager.getInstance().asyncRefresh(null);
    }

    private void notify(String content, NotificationType type) {
        var n = new Notification(NOTIFICATION_GROUP, "Qilletni Libraries", content, type);
        Notifications.Bus.notify(n, ProjectManager.getInstance().getDefaultProject());
    }

    private record ScanItem(String name, Path archive, InstalledQllLibrary installed, List<LibraryLoadError> errors) {}

    private ScanItem scanArchive(Path archivePath) {
        List<LibraryLoadError> errors = new ArrayList<>();
        try {
            if (!Files.isRegularFile(archivePath)) {
                errors.add(new LibraryLoadError(archivePath, "Archive not found"));
                return new ScanItem("<unknown>", archivePath, null, errors);
            }

            // Read qll.info directly from ZIP (no VFS mount)
            QllInfo info;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archivePath.toFile())) {
                var infoEntry = zip.getEntry("qll.info");
                if (infoEntry == null) {
                    errors.add(new LibraryLoadError(archivePath, "Missing qll.info"));
                    return new ScanItem("<unknown>", archivePath, null, errors);
                }
                try (var in = zip.getInputStream(infoEntry); var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    info = gson.fromJson(reader, QllInfo.class);
                }
                if (info == null || info.name() == null || info.name().isBlank()) {
                    errors.add(new LibraryLoadError(archivePath, "qll.info missing name"));
                    return new ScanItem("<unknown>", archivePath, null, errors);
                }

                // Ensure a persistent extraction cache exists and is up-to-date; extract only qilletni-src/**
                var cacheRoot = cacheDirFor(archivePath);
                var srcDir = ensureExtractedQilletniSrc(zip, archivePath, cacheRoot, errors);
                com.intellij.openapi.vfs.VirtualFile srcRootVf = null;
                if (srcDir != null) {
                    srcRootVf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(srcDir);
                }

                // Ensure native.jar is available as a proper jar root by extracting it to cache and mounting it
                com.intellij.openapi.vfs.VirtualFile nativeJarRootVf = null;
                try {
                    var nativeJarEntry = zip.getEntry("native.jar");
                    if (nativeJarEntry != null && !nativeJarEntry.isDirectory()) {
                        var outJar = cacheRoot.resolve("native.jar");
                        // Write/overwrite cached jar (small file; can optimize later if needed)
                        try (var in = zip.getInputStream(nativeJarEntry); var os = java.nio.file.Files.newOutputStream(outJar)) {
                            in.transferTo(os);
                        }
                        var url = "jar://" + outJar.toAbsolutePath().toString().replace('\\', '/') + "!/";
                        nativeJarRootVf = com.intellij.openapi.vfs.VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
                        if (nativeJarRootVf != null) {
                            var children = nativeJarRootVf.getChildren();
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                var installed = new InstalledQllLibrary(
                        info.name(),
                        info.version(),
                        info.author(),
                        info.description(),
                        archivePath,
                        srcRootVf,
                        nativeJarRootVf,
                        info.nativeClasses() == null ? List.of() : List.copyOf(info.nativeClasses()),
                        info.autoImportFiles() == null ? List.of() : List.copyOf(info.autoImportFiles())
                );
                return new ScanItem(info.name(), archivePath, installed, errors);
            } catch (JsonSyntaxException ex) {
                errors.add(new LibraryLoadError(archivePath, "Invalid qll.info: " + ex.getMessage()));
                return new ScanItem("<unknown>", archivePath, null, errors);
            }
        } catch (Throwable t) {
            errors.add(new LibraryLoadError(archivePath, "Failed to scan: " + t.getMessage()));
            return new ScanItem("<unknown>", archivePath, null, errors);
        }
    }

    /** Compute stable cache dir under IDE system path for an archive (path+mtime+size hash). */
    private static Path cacheDirFor(Path archivePath) throws IOException {
        var sys = com.intellij.openapi.application.PathManager.getSystemPath();
        var attrs = java.nio.file.Files.readAttributes(archivePath, java.nio.file.attribute.BasicFileAttributes.class);
        var key = archivePath.toAbsolutePath().toString() + "|" + attrs.lastModifiedTime().toMillis() + "|" + attrs.size();
        var digest = sha256Hex(key);
        var dir = Path.of(sys, "qilletni-libs", digest);
        java.nio.file.Files.createDirectories(dir);
        return dir;
    }

    private static String sha256Hex(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            var bytes = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Fallback: simple hash
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Extracts qilletni-src/** from the given zip into cacheRoot; returns the extracted qilletni-src dir path or null. */
    private static Path ensureExtractedQilletniSrc(java.util.zip.ZipFile zip, Path archivePath, Path cacheRoot, List<LibraryLoadError> errors) {
        try {
            var meta = cacheRoot.resolve(".qll.meta");
            var attrs = java.nio.file.Files.readAttributes(archivePath, java.nio.file.attribute.BasicFileAttributes.class);
            var stamp = attrs.lastModifiedTime().toMillis() + ":" + attrs.size();
            boolean upToDate = java.nio.file.Files.isRegularFile(meta) && java.nio.file.Files.readString(meta).equals(stamp);
            var outSrc = cacheRoot.resolve("qilletni-src");
            if (!upToDate) {
                // Clean old cache
                if (java.nio.file.Files.exists(outSrc)) {
                    deleteRecursively(outSrc);
                }
                java.nio.file.Files.createDirectories(outSrc);
                // Extract entries under qilletni-src/
                var enumEntries = zip.entries();
                while (enumEntries.hasMoreElements()) {
                    var e = enumEntries.nextElement();
                    String name = e.getName();
                    if (!name.startsWith("qilletni-src/")) continue;
                    var rel = name.substring("qilletni-src/".length());
                    var target = outSrc.resolve(rel);
                    if (e.isDirectory()) {
                        java.nio.file.Files.createDirectories(target);
                    } else {
                        java.nio.file.Files.createDirectories(target.getParent());
                        try (var in = zip.getInputStream(e); var out = java.nio.file.Files.newOutputStream(target)) {
                            in.transferTo(out);
                        }
                    }
                }
                java.nio.file.Files.writeString(meta, stamp);
            }
            return java.nio.file.Files.exists(outSrc) ? outSrc : null;
        } catch (IOException io) {
            errors.add(new LibraryLoadError(archivePath, "Extraction failed: " + io.getMessage()));
            return null;
        }
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!java.nio.file.Files.exists(p)) return;
        try (var walk = java.nio.file.Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { java.nio.file.Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        }
    }

    // Future: support unzipped library folders (containing qll.info and qilletni-src) alongside .qll archives.
    // This can be implemented by checking Files.isDirectory and reading files directly via LocalFileSystem.
}
