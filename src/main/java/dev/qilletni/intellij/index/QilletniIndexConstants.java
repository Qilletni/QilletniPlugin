package dev.qilletni.intellij.index;

/**
 * Names and versions of the stub indexes, and helpers for composing their keys.
 */
public final class QilletniIndexConstants {
    private QilletniIndexConstants() {}

    // Index names
    public static final String ENTITY_INDEX_NAME = "qilletni.entity";
    public static final String FUNCTION_INDEX_NAME = "qilletni.function"; // top-level only
    public static final String EXT_METHOD_INDEX_NAME = "qilletni.extMethod"; // key: receiver#name

    /** Version of the stub tree; bump when stub contents or serialization change. */
    public static final int STUB_VERSION = 2;

    // Versions (bump when serialization/keying changes)
    public static final int ENTITY_INDEX_VERSION = 1;
    public static final int FUNCTION_INDEX_VERSION = 1;
    public static final int EXT_METHOD_INDEX_VERSION = 4;

    /**
     * Compose a composite key for extension methods in the form "Receiver#name".
     * Returns an empty string if inputs are null/blank to avoid null-handling at call sites.
     */
    public static String extMethodKey(String receiverSimple, String methodName) {
        if (receiverSimple == null || methodName == null) return "";
        var r = receiverSimple.trim();
        var n = methodName.trim();
        if (r.isEmpty() || n.isEmpty()) return "";
        return r + "#" + n;
    }
}
