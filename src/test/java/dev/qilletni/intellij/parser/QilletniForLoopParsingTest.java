package dev.qilletni.intellij.parser;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.qilletni.intellij.psi.QilletniForStmt;

/** All three {@code for} headers parse, and code after them isn't swallowed by an error. */
public class QilletniForLoopParsingTest extends BasePlatformTestCase {

    private void assertParses(String text, int loops) {
        var file = myFixture.configureByText("a.ql", text);
        var errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
        assertTrue("Unexpected parse errors: " + errors.stream().map(PsiErrorElement::getErrorDescription).toList(), errors.isEmpty());
        assertEquals(loops, PsiTreeUtil.findChildrenOfType(file, QilletniForStmt.class).size());
    }

    public void testRangeLoop() {
        assertParses("for (i..10) {\n    print(i)\n}\nprint(1)\n", 1);
    }

    public void testInfiniteRangeLoop() {
        assertParses("for (i..infinity) {\n    print(i)\n}\n", 1);
    }

    public void testForeachLoop() {
        assertParses("string[] names = [\"a\", \"b\"]\nfor (name : names) {\n    print(name)\n}\n", 1);
    }

    public void testConditionLoop() {
        assertParses("int i = 0\nfor (i < 3) {\n    i = i + 1\n}\n", 1);
    }

    public void testNestedLoops() {
        assertParses("for (i..3) {\n    for (j..i) {\n        print(j)\n    }\n}\n", 2);
    }
}
