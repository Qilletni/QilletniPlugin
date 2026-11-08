package dev.qilletni.intellij.resolve;

import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.resolve.impl.QilletniReverseNativeResolver;
import dev.qilletni.intellij.toolchain.QilletniToolchainSettings;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Java methods in an installed library's native.jar resolve back to that library's native functions. */
public class QilletniReverseNativeResolverTest extends BasePlatformTestCase {
    private String previousHome;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var settings = QilletniToolchainSettings.getInstance();
        previousHome = settings.getQilletniHome();
        var home = Files.createTempDirectory("qilletni-home");
        settings.setQilletniHome(home.toString());

        var archive = Files.createDirectories(home.resolve("packages/qilletni/lib")).resolve("lib-1.0.0.qll");
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("qll.info", utf8("{\"scope\":\"qilletni\",\"name\":\"lib\",\"version\":\"1.0.0\",\"nativeClasses\":[\"p.Natives\"]}"));
        entries.put("qilletni-src/lib.ql", utf8("native fun hello()\n\nnative fun unbound()\n"));
        entries.put("native/p/Natives.class", nativesClass());
        entries.put("native/p/Other.class", emptyClass("p/Other", "hello"));
        Files.write(archive, zip(entries));

        myFixture.addFileToProject("qilletni-src/qilletni_info.yml", "name: app\nversion: 1.0.0\ndependencies:\n  qilletni/lib: ^1.0.0\n");
        myFixture.addFileToProject("qilletni-src/qilletni.lock",
                "packages:\n  qilletni/lib@1.0.0:\n    version: 1.0.0\n    resolved: qilletni/lib/lib-1.0.0\n");
        recompute();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            QilletniToolchainSettings.getInstance().setQilletniHome(previousHome);
            // The light project is reused by other tests; don't leave the library attached
            var src = myFixture.findFileInTempDir("qilletni-src");
            if (src != null) {
                WriteAction.run(() -> src.delete(this));
            }
            recompute();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private void recompute() {
        QilletniProjectDependencies.getInstance(getProject()).recomputeNow();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    }

    public void testLibraryMethodResolvesToNativeFunction() {
        var targets = QilletniReverseNativeResolver.resolveQilletniFunctions(method("p.Natives", "hello"));
        assertEquals(1, targets.size());
        var def = targets.getFirst();
        assertEquals("lib.ql", def.getContainingFile().getName());
        assertEquals("hello", def.getFunctionName().getId().getText());
    }

    public void testMethodWithoutNativeFunctionResolvesToNothing() {
        assertEmpty(QilletniReverseNativeResolver.resolveQilletniFunctions(method("p.Natives", "helper")));
    }

    public void testClassNotListedInNativeClassesResolvesToNothing() {
        assertEmpty(QilletniReverseNativeResolver.resolveQilletniFunctions(method("p.Other", "hello")));
    }

    private PsiMethod method(String fqn, String name) {
        var cls = JavaPsiFacade.getInstance(getProject()).findClass(fqn, GlobalSearchScope.allScope(getProject()));
        assertNotNull("class " + fqn + " from the library's native.jar", cls);
        var methods = cls.findMethodsByName(name, false);
        assertEquals(1, methods.length);
        return methods[0];
    }

    private static byte[] nativesClass() {
        return emptyClass("p/Natives", "hello", "helper");
    }

    /** A public class with the given {@code public static void m()} methods. */
    private static byte[] emptyClass(String internalName, String... methods) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        for (var name : methods) {
            var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
