package dev.qilletni.intellij.util;

import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.model.QilletniManifest;

/**
 * Directory and manifest names that make up a Qilletni project layout.
 */
public final class QilletniConstants {
    private QilletniConstants() {}

    /** Source directory of a Qilletni project or library. */
    public static final String QILLETNI_SRC = QilletniProjectDependencies.SRC_DIR;
    /** Directory of example programs, which may import the enclosing project as a library. */
    public static final String EXAMPLES = "examples";
    /** {@code qilletni_info.yml}, the manifest at the top of {@link #QILLETNI_SRC}. */
    public static final String MANIFEST_FILE = QilletniManifest.FILE_NAME;
    /** Key in {@code qilletni_info.yml} listing the Java classes that implement native functions. */
    public static final String NATIVE_CLASSES_KEY = "native_classes";
}
