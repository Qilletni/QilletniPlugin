package dev.qilletni.intellij.psi.stubs.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import dev.qilletni.intellij.QilletniLanguage;
import dev.qilletni.intellij.index.QilletniEntityIndex;
import dev.qilletni.intellij.index.QilletniIndexConstants;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniEntityName;
import dev.qilletni.intellij.psi.impl.QilletniEntityDefImpl;
import dev.qilletni.intellij.psi.stubs.QilletniEntityDefStub;
import dev.qilletni.intellij.psi.stubs.impl.QilletniEntityDefStubImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class QilletniEntityDefElementType extends IStubElementType<QilletniEntityDefStub, QilletniEntityDef> {
    public QilletniEntityDefElementType() {
        super("ENTITY_DEF", QilletniLanguage.INSTANCE);
    }

    @Override
    public QilletniEntityDef createPsi(@NotNull QilletniEntityDefStub stub) {
        return new QilletniEntityDefImpl(stub, this);
    }

    @Override
    public @NotNull QilletniEntityDefStub createStub(@NotNull QilletniEntityDef psi, StubElement<?> parentStub) {
        String name = null;
        QilletniEntityName nm = psi.getEntityName();
        if (nm != null) {
            PsiElement id = nm.getId();
            name = id != null ? id.getText() : null;
        }
        return new QilletniEntityDefStubImpl(parentStub, this, name == null ? "" : name);
    }

    @Override
    public @NotNull String getExternalId() {
        return QilletniIndexConstants.ENTITY_INDEX_NAME + ".entityDef";
    }

    @Override
    public void serialize(@NotNull QilletniEntityDefStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        String name = (stub instanceof QilletniEntityDefStubImpl s) ? s.getName() : "";
        dataStream.writeName(name);
    }

    @Override
    public @NotNull QilletniEntityDefStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        StringRef ref = dataStream.readName();
        String name = ref == null ? "" : ref.getString();
        return new QilletniEntityDefStubImpl(parentStub, this, name);
    }

    @Override
    public void indexStub(@NotNull QilletniEntityDefStub stub, @NotNull IndexSink sink) {
        if (stub instanceof QilletniEntityDefStubImpl s) {
            String name = s.getName();
            if (name != null && !name.isBlank()) {
                sink.occurrence(QilletniEntityIndex.KEY, name);
            }
        }
    }
}