package dev.qilletni.intellij.debugger;

import com.intellij.openapi.util.TextRange;
import junit.framework.TestCase;

public class QilletniDebuggerEvaluatorTest extends TestCase {

    private static String hover(String text, String identifier) {
        int start = text.indexOf(identifier);
        var range = QilletniDebuggerEvaluator.propertyChainRange(text, new TextRange(start, start + identifier.length()));
        return range == null ? null : range.substring(text);
    }

    public void testPlainName() {
        assertEquals("count", hover("print(count)", "count"));
    }

    public void testPropertyChain() {
        assertEquals("user.address.city", hover("x = user.address.city + 1", "city"));
    }

    public void testCallsAreNotHoverable() {
        assertNull(hover("x = compute (1)", "compute"));
        assertNull(hover("x = load().name", "name"));
    }
}
