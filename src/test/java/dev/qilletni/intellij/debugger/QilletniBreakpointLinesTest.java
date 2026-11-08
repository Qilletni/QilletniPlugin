package dev.qilletni.intellij.debugger;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

public class QilletniBreakpointLinesTest extends BasePlatformTestCase {

    private List<Integer> breakableLines(String text) {
        var file = myFixture.configureByText("a.ql", text);
        var document = myFixture.getEditor().getDocument();
        var lines = new ArrayList<Integer>();
        for (int line = 0; line < document.getLineCount(); line++) {
            if (QilletniBreakpointLines.isBreakable(file, document, line)) lines.add(line);
        }
        return lines;
    }

    public void testStatementsAndReturnsAreBreakable() {
        var lines = breakableLines("""
                import "other.ql"
                // a comment
                fun add(a, b) {
                    int sum = a + b
                    return sum
                }

                int x = add(1, 2)
                print(x)
                """);
        // 3: assignment, 4: return, 7: top-level assignment, 8: call
        assertEquals(List.of(3, 4, 7, 8), lines);
    }

    public void testControlFlowHeadersAndBodies() {
        var lines = breakableLines("""
                for (i < 3) {
                    print(i)
                }
                if (true) {
                    print(1)
                } else {
                    print(2)
                }
                """);
        assertEquals(List.of(0, 1, 3, 4, 6), lines);
    }

    public void testEntityDefinitionsAreNotBreakable() {
        var lines = breakableLines("""
                entity Box {
                    int size
                    Box(size)
                    fun grow() {
                        size = size + 1
                    }
                }
                Box b = new Box(1)
                """);
        // Only the method body and the top-level statement
        assertEquals(List.of(4, 7), lines);
    }

    public void testLinesOutsideTheDocument() {
        var file = myFixture.configureByText("a.ql", "print(1)\n");
        var document = myFixture.getEditor().getDocument();
        assertFalse(QilletniBreakpointLines.isBreakable(file, document, -1));
        assertFalse(QilletniBreakpointLines.isBreakable(file, document, 5));
    }
}
