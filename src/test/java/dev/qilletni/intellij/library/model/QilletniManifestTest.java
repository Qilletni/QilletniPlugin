package dev.qilletni.intellij.library.model;

import junit.framework.TestCase;

import java.util.List;

public class QilletniManifestTest extends TestCase {

    public void testParseManifest() {
        var manifest = QilletniManifest.parse("""
                name: qilletni/http
                version: 1.0.0
                author: Someone
                native_classes:
                  - dev.qilletni.lib.http.Http
                dependencies:
                  qilletni/std: ^1.0.0
                  qilletni/json: 1.0.0
                """);
        assertNotNull(manifest);
        assertEquals("qilletni/http", manifest.name());
        assertEquals("http", manifest.simpleName());
        assertEquals("1.0.0", manifest.version());
        assertEquals(List.of("dev.qilletni.lib.http.Http"), manifest.nativeClasses());
        assertEquals(List.of("qilletni/std", "qilletni/json"), List.copyOf(manifest.dependencies().keySet()));
        assertEquals("^1.0.0", manifest.dependencies().get("qilletni/std"));
    }

    public void testInvalidManifest() {
        assertNull(QilletniManifest.parse("- just\n- a list"));
        assertNull(QilletniManifest.parse("name: [unclosed"));
    }

    public void testParseLockfile() {
        var lockfile = QilletniLockfile.parse("""
                version: 1
                packages:
                  qilletni/std@1.0.0:
                    version: 1.0.0
                    resolved: qilletni/std/std-1.0.0
                    integrity: sha256-abc
                    dependencies: {}
                  qilletni/spotify@1.0.1:
                    version: 1.0.1
                    resolved: qilletni/spotify/spotify-1.0.1
                """);
        assertNotNull(lockfile);
        assertEquals(2, lockfile.packages().size());
        var std = lockfile.packages().getFirst();
        assertEquals("qilletni/std", std.id());
        assertEquals("1.0.0", std.version());
        assertEquals("qilletni/std/std-1.0.0", std.resolved());
        assertTrue(lockfile.contains("qilletni/spotify"));
        assertFalse(lockfile.contains("qilletni/postgres"));
    }
}
