package dev.qilletni.intellij.index;

import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class QilletniStubIndexTest extends BasePlatformTestCase {

    public void testFunctionsEntitiesAndExtensionsAreIndexed() {
        myFixture.configureByText("lib.ql", """
                fun topLevel() {
                }

                fun shout(s) on string {
                    return s
                }

                entity Counter {
                    int count = 0

                    Counter()

                    fun increment() {
                        count = count + 1
                    }
                }
                """);
        var project = getProject();
        var scope = GlobalSearchScope.allScope(project);
        assertTrue(StubIndex.getInstance().getAllKeys(QilletniFunctionIndex.KEY, project).contains("topLevel"));
        assertFalse("entity methods are not top-level functions",
                StubIndex.getInstance().getAllKeys(QilletniFunctionIndex.KEY, project).contains("increment"));
        assertTrue(StubIndex.getInstance().getAllKeys(QilletniEntityIndex.KEY, project).contains("Counter"));
        assertTrue(StubIndex.getInstance().getAllKeys(QilletniExtMethodIndex.KEY, project).contains(QilletniIndexConstants.extMethodKey("string", "shout")));
        assertEquals(1, StubIndex.getElements(QilletniFunctionIndex.KEY, "topLevel", project, scope, dev.qilletni.intellij.psi.QilletniFunctionDef.class).size());
    }
}
