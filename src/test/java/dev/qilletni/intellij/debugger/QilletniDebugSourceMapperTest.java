package dev.qilletni.intellij.debugger;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.qilletni.intellij.library.model.InstalledQllLibrary;

import java.nio.file.Files;
import java.util.List;

public class QilletniDebugSourceMapperTest extends BasePlatformTestCase {

    public void testLibraryFilesUseQllPaths() {
        var file = myFixture.addFileToProject("std-src/util/map.ql", "entity Map {}\n").getVirtualFile();
        var srcRoot = file.getParent().getParent();
        var library = new InstalledQllLibrary("", "std", "1.0.0", "", "", null, srcRoot, null, List.of(), List.of());
        var mapper = new QilletniDebugSourceMapper(() -> List.of(library));

        assertEquals("qll://std/util/map.ql", mapper.toDapPath(file));
        assertEquals(file, mapper.toFile("qll://std/util/map.ql"));
        assertNull(mapper.toFile("qll://std/util/missing.ql"));
        assertNull(mapper.toFile("qll://other/util/map.ql"));
        assertNull(mapper.toFile("qll://std"));
    }

    public void testDiskFilesUseRealPaths() throws Exception {
        var dir = Files.createTempDirectory("qilletni-mapper");
        var script = Files.writeString(dir.resolve("main.ql"), "print(1)\n");
        try {
            var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(script);
            assertNotNull(file);
            var mapper = new QilletniDebugSourceMapper(List::of);

            var path = mapper.toDapPath(file);
            assertEquals(script.toRealPath().toString(), path);
            assertEquals(file, mapper.toFile(path));
        } finally {
            Files.deleteIfExists(script);
            Files.deleteIfExists(dir);
        }
    }

    public void testUnknownSources() {
        var mapper = new QilletniDebugSourceMapper(List::of);
        assertNull(mapper.toFile((String) null));
        assertNull(mapper.toFile(""));
        assertNull(mapper.toFile((org.eclipse.lsp4j.debug.Source) null));
    }
}
