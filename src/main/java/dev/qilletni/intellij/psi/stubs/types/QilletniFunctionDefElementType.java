package dev.qilletni.intellij.psi.stubs.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.io.StringRef;
import dev.qilletni.intellij.QilletniLanguage;
import dev.qilletni.intellij.index.QilletniExtMethodIndex;
import dev.qilletni.intellij.index.QilletniFunctionIndex;
import dev.qilletni.intellij.index.QilletniIndexConstants;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.impl.QilletniFunctionDefImpl;
import dev.qilletni.intellij.psi.stubs.QilletniFunctionDefStub;
import dev.qilletni.intellij.psi.stubs.impl.QilletniFunctionDefStubImpl;
import dev.qilletni.intellij.resolve.QilletniPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class QilletniFunctionDefElementType extends IStubElementType<QilletniFunctionDefStub, QilletniFunctionDef> {
    public QilletniFunctionDefElementType() {
        super("FUNCTION_DEF", QilletniLanguage.INSTANCE);
    }

    @Override
    public QilletniFunctionDef createPsi(@NotNull QilletniFunctionDefStub stub) {
        return new QilletniFunctionDefImpl(stub, this);
    }

    @Override
    public @NotNull QilletniFunctionDefStub createStub(@NotNull QilletniFunctionDef psi, StubElement<?> parentStub) {
        var functionName = psi.getFunctionName();
        String name = functionName != null && functionName.getId() != null ? functionName.getId().getText() : null;
        boolean isExt = psi.getFunctionOnType() != null;
        String receiver = null;
        if (isExt) {
            // The receiver is the last type token of the on-clause; qualifiers are not part of the key
            PsiElement id = QilletniPsiUtil.receiverTypeToken(psi.getFunctionOnType());
            receiver = id != null ? id.getText() : null;
        }
        boolean inEntity = PsiTreeUtil.getParentOfType(psi, QilletniEntityDef.class, false) != null;
        return new QilletniFunctionDefStubImpl(parentStub, this, name == null ? "" : name, isExt, receiver == null ? "" : receiver, inEntity);
    }

    @Override
    public @NotNull String getExternalId() {
        return QilletniIndexConstants.FUNCTION_INDEX_NAME + ".functionDef";
    }

    @Override
    public void serialize(@NotNull QilletniFunctionDefStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        if (stub instanceof QilletniFunctionDefStubImpl s) {
            dataStream.writeName(s.getName());
            dataStream.writeBoolean(s.isExtension());
            dataStream.writeName(s.getReceiverSimple());
            dataStream.writeBoolean(s.isInEntity());
        } else {
            dataStream.writeName("");
            dataStream.writeBoolean(false);
            dataStream.writeName("");
            dataStream.writeBoolean(false);
        }
    }

    @Override
    public @NotNull QilletniFunctionDefStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = StringRef.toString(dataStream.readName());
        boolean isExt = dataStream.readBoolean();
        String recv = StringRef.toString(dataStream.readName());
        boolean inEntity = dataStream.readBoolean();
        return new QilletniFunctionDefStubImpl(parentStub, this, name == null ? "" : name, isExt, recv == null ? "" : recv, inEntity);
    }

    @Override
    public void indexStub(@NotNull QilletniFunctionDefStub stub, @NotNull IndexSink sink) {
        if (!(stub instanceof QilletniFunctionDefStubImpl s)) return;
        String name = s.getName();
        if (name != null && !name.isBlank()) {
            if (!s.isExtension() && !s.isInEntity()) {
                sink.occurrence(QilletniFunctionIndex.KEY, name);
            }
            if (s.isExtension()) {
                String recv = s.getReceiverSimple();
                String key = QilletniIndexConstants.extMethodKey(recv, name);
                if (!key.isBlank()) {
                    sink.occurrence(QilletniExtMethodIndex.KEY, key);
                }
            }
        }
    }
}