package dev.qilletni.intellij.library;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.qilletni.intellij.library.model.InstalledQllLibrary;
import dev.qilletni.intellij.library.model.QilletniLockfile;
import dev.qilletni.intellij.library.model.QilletniManifest;
import dev.qilletni.intellij.library.model.QllInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QilletniDependencyResolverTest extends BasePlatformTestCase {
    private Path local;
    private Path packages;
    private final Map<Path, QllInfo> infos = new HashMap<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var home = Files.createTempDirectory("qilletni-home");
        local = Files.createDirectories(home.resolve("packages-local"));
        packages = Files.createDirectories(home.resolve("packages"));
    }

    private Path install(Path base, String scope, String name, String version, QllInfo.Dependency... deps) throws IOException {
        var archive = Files.createDirectories(base.resolve(scope).resolve(name)).resolve(name + "-" + version + ".qll");
        Files.writeString(archive, "");
        infos.put(archive, new QllInfo(scope, name, version, null, null, null, List.of(deps), null, null, List.of(), List.of()));
        return archive;
    }

    private QilletniDependencyResolver resolver() {
        return new QilletniDependencyResolver(List.of(local, packages),
                archive -> {
                    var info = infos.get(archive);
                    return new QilletniLibraryManager.LoadResult(new InstalledQllLibrary(info.scope(), info.name(), info.version(),
                            null, null, archive, null, null, List.of(), List.of()), null);
                },
                infos::get);
    }

    private static QilletniManifest manifest(String deps) {
        return QilletniManifest.parse("name: app\nversion: 1.0.0\ndependencies:\n" + deps);
    }

    public void testLockedPackagesPreferLocalDirectory() throws IOException {
        install(packages, "qilletni", "std", "1.0.0");
        var localStd = install(local, "qilletni", "std", "1.0.0");
        var lockfile = QilletniLockfile.parse("packages:\n  qilletni/std@1.0.0:\n    version: 1.0.0\n    resolved: qilletni/std/std-1.0.0\n");

        var result = resolver().resolve(manifest("  qilletni/std: ^1.0.0\n"), lockfile);

        assertEquals(QilletniProjectModel.Status.OK, result.status());
        assertEquals(localStd, result.dependencies().getFirst().archive());
        assertEquals("^1.0.0", result.dependencies().getFirst().requestedRange());
    }

    public void testMissingLockedPackage() {
        var lockfile = QilletniLockfile.parse("packages:\n  qilletni/std@1.0.0:\n    version: 1.0.0\n    resolved: qilletni/std/std-1.0.0\n");
        var result = resolver().resolve(manifest("  qilletni/std: ^1.0.0\n"), lockfile);
        assertEquals(QilletniProjectModel.Status.MISSING_PACKAGES, result.status());
        assertTrue(result.dependencies().getFirst().isMissing());
    }

    public void testManifestDependencyNotInLockfileIsStale() throws IOException {
        install(packages, "qilletni", "std", "1.0.0");
        var lockfile = QilletniLockfile.parse("packages:\n  qilletni/std@1.0.0:\n    version: 1.0.0\n    resolved: qilletni/std/std-1.0.0\n");
        var result = resolver().resolve(manifest("  qilletni/std: ^1.0.0\n  qilletni/json: ^1.0.0\n"), lockfile);
        assertEquals(QilletniProjectModel.Status.LOCK_STALE, result.status());
    }

    public void testLegacyResolvedPathStillFindsPackage() throws IOException {
        var std = install(packages, "qilletni", "std", "1.0.0-SNAPSHOT");
        var lockfile = QilletniLockfile.parse("packages:\n  qilletni/std@1.0.0-SNAPSHOT:\n    version: 1.0.0-SNAPSHOT\n    resolved: qilletni/std/1.0.0-SNAPSHOT\n");
        var result = resolver().resolve(manifest("  qilletni/std: 1.0.0-SNAPSHOT\n"), lockfile);
        assertEquals(QilletniProjectModel.Status.LOCK_STALE, result.status());
        assertEquals(std, result.dependencies().getFirst().archive());
        assertNotNull(result.dependencies().getFirst().problem());
    }

    public void testWithoutLockfilePicksHighestMatchingVersionAndFollowsTransitives() throws IOException {
        install(packages, "qilletni", "std", "1.0.0");
        var newest = install(packages, "qilletni", "std", "1.2.0");
        install(packages, "qilletni", "std", "2.0.0");
        install(packages, "qilletni", "spotify", "1.0.1", new QllInfo.Dependency("qilletni", "std", "^1.0.0"));

        var result = resolver().resolve(manifest("  qilletni/spotify: ^1.0.0\n"), null);

        assertEquals(QilletniProjectModel.Status.NO_LOCKFILE, result.status());
        assertEquals(2, result.dependencies().size());
        var std = result.dependencies().stream().filter(d -> d.id().equals("qilletni/std")).findFirst().orElseThrow();
        assertEquals(newest, std.archive());
        assertFalse(std.isDirect());
    }
}
