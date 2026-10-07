package dev.qilletni.intellij.library.model;

import junit.framework.TestCase;

public class QllVersionTest extends TestCase {

    public void testParse() {
        assertEquals(new QllVersion(1, 2, 3, null), QllVersion.parse("1.2.3").orElseThrow());
        assertEquals(new QllVersion(1, 0, 0, "SNAPSHOT"), QllVersion.parse("1.0.0-SNAPSHOT").orElseThrow());
        assertTrue(QllVersion.parse("1.0").isEmpty());
        assertTrue(QllVersion.parse("abc").isEmpty());
        assertTrue(QllVersion.parse(null).isEmpty());
        assertTrue(QllVersion.parse("99999999999.0.0").isEmpty());
    }

    public void testOrdering() {
        var snapshot = QllVersion.parse("1.0.0-SNAPSHOT").orElseThrow();
        var release = QllVersion.parse("1.0.0").orElseThrow();
        var patch = QllVersion.parse("1.0.1").orElseThrow();
        assertTrue(snapshot.compareTo(release) < 0);
        assertTrue(release.compareTo(patch) < 0);
        assertEquals("1.0.0-SNAPSHOT", snapshot.toString());
    }

    public void testSatisfies() {
        var v = QllVersion.parse("1.2.3").orElseThrow();
        assertTrue(v.satisfies("^1.0.0"));
        assertFalse(v.satisfies("^2.0.0"));
        assertFalse(v.satisfies("^1.3.0"));
        assertTrue(v.satisfies("~1.2.0"));
        assertFalse(v.satisfies("~1.1.0"));
        assertTrue(v.satisfies(">=1.2.3"));
        assertTrue(v.satisfies("1.2.3"));
        assertFalse(v.satisfies("1.2.4"));
        assertTrue(v.satisfies(null));
        assertTrue(v.satisfies("*"));
        // 0.x carets only allow patch updates
        assertFalse(QllVersion.parse("0.3.0").orElseThrow().satisfies("^0.2.0"));
    }
}
