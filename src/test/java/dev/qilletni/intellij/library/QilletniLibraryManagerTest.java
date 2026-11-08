package dev.qilletni.intellij.library;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class QilletniLibraryManagerTest extends BasePlatformTestCase {

    private static Path archive(Map<String, String> entries) throws IOException {
        var file = Files.createTempFile("lib", ".qll");
        Files.write(file, zipBytes(entries));
        return file;
    }

    private static byte[] zipBytes(Map<String, ?> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                var value = entry.getValue();
                zip.write(value instanceof byte[] b ? b : value.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public void testRepacksNativeDirectoryIntoJar() throws IOException {
        var file = archive(Map.of(
                "qll.info", "{\"name\":\"std\",\"version\":\"1.0.0\"}",
                "native/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n",
                "native/a/b/C.class", "not really a class",
                "qilletni-src/std.ql", "native fun hello()"));
        var lib = QilletniLibraryManager.getInstance().load(file).library();
        assertNotNull(lib);
        var root = lib.nativeJarRoot();
        assertNotNull(root);
        assertNotNull(VfsUtilCore.findRelativeFile("a/b/C.class", root));
        assertNull(root.findChild("native"));
        assertNotNull(lib.srcRoot().findChild("std.ql"));
    }

    public void testLoadsLegacyNativeJar() throws IOException {
        var file = Files.createTempFile("lib", ".qll");
        Files.write(file, zipBytes(Map.of(
                "qll.info", "{\"name\":\"old\",\"version\":\"1.0.0\"}",
                "native.jar", zipBytes(Map.of("a/C.class", "not really a class")))));
        var lib = QilletniLibraryManager.getInstance().load(file).library();
        assertNotNull(lib);
        assertNotNull(lib.nativeJarRoot());
        assertNotNull(VfsUtilCore.findRelativeFile("a/C.class", lib.nativeJarRoot()));
    }

    public void testWithoutNativeClassesHasNoJar() throws IOException {
        var file = archive(Map.of("qll.info", "{\"name\":\"plain\",\"version\":\"1.0.0\"}", "qilletni-src/a.ql", ""));
        var lib = QilletniLibraryManager.getInstance().load(file).library();
        assertNotNull(lib);
        assertNull(lib.nativeJarRoot());
    }

    public void testReextractsCacheWrittenByOlderFormat() throws IOException {
        var file = archive(Map.of(
                "qll.info", "{\"name\":\"std\",\"version\":\"1.0.0\"}",
                "native/a/C.class", "not really a class"));
        var manager = QilletniLibraryManager.getInstance();
        assertNotNull(manager.load(file).library());

        // Simulate a cache written before native/ was supported: old stamp format and no native.jar
        var cacheDir = QilletniLibraryManager.cacheDirFor(file);
        var attrs = Files.readAttributes(file, BasicFileAttributes.class);
        Files.writeString(cacheDir.resolve(".qll.meta"), attrs.lastModifiedTime().toMillis() + ":" + attrs.size());
        Files.delete(cacheDir.resolve("native.jar"));
        // Invalidates the loaded jar root, so the next load goes back to the disk cache
        LocalFileSystem.getInstance().refreshNioFiles(List.of(cacheDir.resolve("native.jar")));

        var lib = manager.load(file).library();
        assertNotNull(lib);
        assertTrue(Files.isRegularFile(cacheDir.resolve("native.jar")));
        assertNotNull(lib.nativeJarRoot());
        assertNotNull(VfsUtilCore.findRelativeFile("a/C.class", lib.nativeJarRoot()));
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
