package dev.qilletni.intellij.library;

import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.model.QilletniLockfile;
import dev.qilletni.intellij.library.model.QilletniManifest;
import dev.qilletni.intellij.library.model.QllInfo;
import dev.qilletni.intellij.library.model.QllVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Resolves a project's dependencies to installed archives, the way the toolchain does.
 * <p>
 * With a lockfile, every locked package (including transitive ones) is loaded from {@code <dir>/<resolved>.qll},
 * trying each packages directory in order. Without one, each declared dependency is matched against the highest
 * installed version satisfying its range, following transitive dependencies from the archives' {@code qll.info}.
 */
final class QilletniDependencyResolver {
    record Result(@NotNull QilletniProjectModel.Status status, @NotNull List<QilletniDependency> dependencies) {}

    private record InstalledVersion(QllVersion version, Path archive) {}

    private final List<Path> packageDirs;
    private final Function<Path, QilletniLibraryManager.LoadResult> loader;
    private final Function<Path, @Nullable QllInfo> infoReader;

    /**
     * @param packageDirs packages directories in priority order ({@code packages-local} before {@code packages})
     * @param loader      loads an archive into a library
     * @param infoReader  reads an archive's {@code qll.info}
     */
    QilletniDependencyResolver(@NotNull List<Path> packageDirs,
                               @NotNull Function<Path, QilletniLibraryManager.LoadResult> loader,
                               @NotNull Function<Path, @Nullable QllInfo> infoReader) {
        this.packageDirs = packageDirs;
        this.loader = loader;
        this.infoReader = infoReader;
    }

    @NotNull Result resolve(@NotNull QilletniManifest manifest, @Nullable QilletniLockfile lockfile) {
        return lockfile != null ? resolveLocked(manifest, lockfile) : resolveUnlocked(manifest);
    }

    private Result resolveLocked(QilletniManifest manifest, QilletniLockfile lockfile) {
        var dependencies = new ArrayList<QilletniDependency>();
        boolean legacyPaths = false;
        for (var entry : lockfile.packages()) {
            var requested = findRange(manifest, entry.id());
            Path archive = entry.resolved() != null ? findLockedArchive(entry.resolved()) : null;
            String problem = null;
            if (archive == null) {
                // Older lockfiles wrote resolved paths without the package name prefix; find the package anyway so
                // the editor keeps working, but flag that qpm install should be re-run.
                archive = findInstalledArchive(entry.id(), entry.version());
                if (archive != null) {
                    legacyPaths = true;
                    problem = QilletniBundle.message("dependency.problem.legacyLock");
                }
            }
            dependencies.add(toDependency(entry.id(), requested, entry.version(), archive, problem));
        }

        boolean stale = manifest.dependencies().keySet().stream().anyMatch(id -> !lockfile.contains(id));
        var status = dependencies.stream().anyMatch(QilletniDependency::isMissing) ? QilletniProjectModel.Status.MISSING_PACKAGES
                : stale || legacyPaths ? QilletniProjectModel.Status.LOCK_STALE
                : QilletniProjectModel.Status.OK;
        return new Result(status, List.copyOf(dependencies));
    }

    private Result resolveUnlocked(QilletniManifest manifest) {
        var dependencies = new ArrayList<QilletniDependency>();
        var seen = new HashSet<String>();
        record Pending(String id, String range, boolean direct) {}
        var queue = new ArrayDeque<Pending>();
        manifest.dependencies().forEach((id, range) -> queue.add(new Pending(id, range, true)));

        while (!queue.isEmpty()) {
            var next = queue.poll();
            if (!seen.add(next.id().toLowerCase(Locale.ROOT))) continue;
            var best = findBestInstalled(next.id(), next.range());
            var dep = toDependency(next.id(), next.direct() ? next.range() : null,
                    best != null ? best.version().toString() : null, best != null ? best.archive() : null, null);
            dependencies.add(dep);

            // Follow transitive dependencies declared in the archive's qll.info
            if (dep.archive() != null) {
                var info = infoReader.apply(dep.archive());
                if (info != null && info.dependencies() != null) {
                    for (var transitive : info.dependencies()) {
                        if (transitive.name() == null) continue;
                        var id = transitive.scope() != null ? transitive.scope() + "/" + transitive.name() : transitive.name();
                        queue.add(new Pending(id, transitive.version(), false));
                    }
                }
            }
        }

        var status = dependencies.stream().anyMatch(QilletniDependency::isMissing)
                ? QilletniProjectModel.Status.MISSING_PACKAGES
                : QilletniProjectModel.Status.NO_LOCKFILE;
        return new Result(status, List.copyOf(dependencies));
    }

    private QilletniDependency toDependency(String id, @Nullable String range, @Nullable String version,
                                            @Nullable Path archive, @Nullable String problem) {
        if (archive == null) {
            return new QilletniDependency(id, range, version, null, null, QilletniBundle.message("dependency.problem.notInstalled"));
        }
        var loaded = loader.apply(archive);
        return new QilletniDependency(id, range, version, archive, loaded.library(),
                loaded.error() != null ? loaded.error() : problem);
    }

    private static @Nullable String findRange(QilletniManifest manifest, String id) {
        for (var e : manifest.dependencies().entrySet()) {
            if (e.getKey().equalsIgnoreCase(id)) return e.getValue();
        }
        return null;
    }

    /** Resolves a lockfile {@code resolved} path exactly like the toolchain does. */
    private @Nullable Path findLockedArchive(String resolved) {
        for (var dir : packageDirs) {
            var archive = dir.resolve(resolved + ".qll");
            if (Files.isRegularFile(archive)) return archive;
        }
        return null;
    }

    private @Nullable Path findInstalledArchive(String id, @Nullable String version) {
        var parsed = QllVersion.parse(version).orElse(null);
        return installedVersions(id).stream()
                .filter(v -> parsed == null || v.version().equals(parsed))
                .map(InstalledVersion::archive)
                .findFirst().orElse(null);
    }

    private @Nullable InstalledVersion findBestInstalled(String id, @Nullable String range) {
        return installedVersions(id).stream()
                .filter(v -> v.version().satisfies(range))
                .max(Comparator.comparing(InstalledVersion::version))
                .orElse(null);
    }

    /** Installed archives of a package, earlier directories first, parsed from {@code <scope>/<name>/<name>-<version>.qll}. */
    private List<InstalledVersion> installedVersions(String id) {
        int slash = id.indexOf('/');
        var name = slash >= 0 ? id.substring(slash + 1) : id;
        var result = new ArrayList<InstalledVersion>();
        Set<QllVersion> seen = new HashSet<>();
        for (var base : packageDirs) {
            var dir = base.resolve(id);
            if (!Files.isDirectory(dir)) continue;
            try (var files = Files.list(dir)) {
                for (var file : files.sorted().toList()) {
                    var fileName = file.getFileName().toString();
                    if (!fileName.endsWith(".qll")) continue;
                    var stem = fileName.substring(0, fileName.length() - ".qll".length());
                    if (stem.startsWith(name + "-")) stem = stem.substring(name.length() + 1);
                    QllVersion.parse(stem).filter(seen::add).ifPresent(v -> result.add(new InstalledVersion(v, file)));
                }
            } catch (IOException ignored) {
                // Unreadable directory: treat as not installed
            }
        }
        return result;
    }
}
