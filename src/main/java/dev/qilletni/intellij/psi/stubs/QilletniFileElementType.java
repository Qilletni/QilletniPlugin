package dev.qilletni.intellij.psi.stubs;

import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.tree.IStubFileElementType;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.QilletniLanguage;
import dev.qilletni.intellij.index.QilletniIndexConstants;
import org.jetbrains.annotations.NotNull;

/**
 * File element type for .ql files. Being a stub file type is what makes the platform build stubs for function and
 * entity definitions, which feed the indexes in {@code dev.qilletni.intellij.index}.
 */
public final class QilletniFileElementType extends IStubFileElementType<PsiFileStub<QilletniFile>> {
    public static final QilletniFileElementType INSTANCE = new QilletniFileElementType();

    private QilletniFileElementType() {
        super("QILLETNI_FILE", QilletniLanguage.INSTANCE);
    }

    @Override
    public int getStubVersion() {
        return QilletniIndexConstants.STUB_VERSION;
    }

    @Override
    public @NotNull String getExternalId() {
        return "qilletni.FILE";
    }
}
