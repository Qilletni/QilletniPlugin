package dev.qilletni.intellij.library;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class QilletniLibraryManagerTest extends BasePlatformTestCase {

    private static Path archive(Map<String, String> entries) throws IOException {
        var file = Files.createTempFile("lib", ".qll");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    public void testLoadsStringVersionAndSources() throws IOException {
        var file = archive(Map.of(
                "qll.info", "{\"scope\":\"qilletni\",\"name\":\"std\",\"version\":\"1.0.0\",\"autoImportFiles\":[\"std.ql\"],\"dependencies\":[{\"scope\":\"qilletni\",\"name\":\"x\",\"version\":\"^1.0.0\"}]}",
                "qilletni-src/std.ql", "fun hello() {}"));
        var result = QilletniLibraryManager.getInstance().load(file);
        assertNull(result.error());
        var lib = result.library();
        assertNotNull(lib);
        assertEquals("std", lib.name());
        assertEquals("qilletni", lib.scope());
        assertEquals("1.0.0", lib.version());
        assertNotNull(lib.srcRoot());
        assertNotNull(lib.srcRoot().findChild("std.ql"));
    }

    public void testLoadsLegacyObjectVersion() throws IOException {
        var file = archive(Map.of("qll.info", "{\"name\":\"old\",\"version\":{\"major\":1,\"minor\":2,\"patch\":3}}"));
        var info = QilletniLibraryManager.getInstance().readInfo(file);
        assertNotNull(info);
        assertEquals("1.2.3", info.version());
    }

    public void testRejectsEntriesOutsideExtractionDirectory() throws IOException {
        var marker = "qilletni-zip-slip-" + System.nanoTime() + ".txt";
        var file = archive(Map.of(
                "qll.info", "{\"name\":\"evil\",\"version\":\"1.0.0\"}",
                "qilletni-src/../../../" + marker, "pwned"));
        var result = QilletniLibraryManager.getInstance().load(file);
        assertNotNull(result.library());
        var cacheParent = QilletniLibraryManager.cacheRoot().getParent();
        assertFalse(Files.exists(cacheParent.resolve(marker)));
        assertFalse(Files.exists(cacheParent.getParent().resolve(marker)));
    }

    public void testMissingInfoIsAnError() throws IOException {
        var file = archive(Map.of("qilletni-src/a.ql", ""));
        var result = QilletniLibraryManager.getInstance().load(file);
        assertNull(result.library());
        assertNotNull(result.error());
    }
}
