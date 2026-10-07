package dev.qilletni.intellij.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniFunctionCall;
import dev.qilletni.intellij.psi.QilletniParamName;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.psi.QilletniVarName;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Infers the static type of the receiver on the left of a member access ({@code receiver.member}).
 * Shared by reference resolution and completion so both see the same receiver.
 */
public final class QilletniTypeInference {
    private QilletniTypeInference() {}

    private static final Map<IElementType, String> NATIVE_TYPE_NAMES = Map.of(
            QilletniTypes.INT_TYPE, "int",
            QilletniTypes.DOUBLE_TYPE, "double",
            QilletniTypes.STRING_TYPE, "string",
            QilletniTypes.BOOLEAN_TYPE, "boolean",
            QilletniTypes.COLLECTION_TYPE, "collection",
            QilletniTypes.SONG_TYPE, "song",
            QilletniTypes.ALBUM_TYPE, "album",
            QilletniTypes.WEIGHTS_KEYWORD, "weights");

    /**
     * A receiver type.
     *
     * @param isTypeReceiver true when the receiver names an entity type itself ({@code Foo.bar()}), so only static
     *                       members apply
     */
    public record Receiver(String type, boolean isTypeReceiver) {}

    /**
     * Infers the type of a {@code primary_expr}: {@code new T(...)}, then (optionally) a built-in type token inside
     * it, then the declared type of the variable or extension receiver parameter it names.
     * Calls are never inferred since return types are unknown.
     *
     * @param nativeTokenFallback whether to look for built-in type tokens inside the primary, which completion uses
     *                            on partially parsed copies
     */
    public static @Nullable String inferReceiverType(@Nullable PsiElement primary, boolean nativeTokenFallback) {
        if (primary == null) return null;
        String t = QilletniTypeUtil.inferStaticType(primary);
        if (t != null) return t;

        if (PsiTreeUtil.findChildOfType(primary, QilletniFunctionCall.class) != null) return null;

        if (nativeTokenFallback) {
            // Direct children first, then any descendant
            for (PsiElement c = primary.getFirstChild(); c != null; c = c.getNextSibling()) {
                var name = nativeTypeName(c);
                if (name != null) return name;
            }
            for (PsiElement e : PsiTreeUtil.findChildrenOfType(primary, PsiElement.class)) {
                var name = nativeTypeName(e);
                if (name != null) return name;
            }
        }

        PsiElement idLeaf = QilletniPsiUtil.findDirectIdChild(primary);
        if (idLeaf != null) {
            PsiElement declName = QilletniResolveUtil.resolveVariableUsage(idLeaf);
            if (declName instanceof QilletniVarName varName) {
                return QilletniPsiUtil.getVarDeclarationType(varName);
            }
            if (declName instanceof QilletniParamName paramName) {
                // The first parameter of an extension function takes the receiver's type
                return QilletniParamTypeUtil.getParamType(paramName).orElse(null);
            }
        }
        return null;
    }

    /**
     * Like {@link #inferReceiverType}, but when no instance type is found and the primary is a bare entity name,
     * returns that entity as a type receiver.
     */
    public static @Nullable Receiver inferReceiver(QilletniFile file, @Nullable PsiElement primary, boolean nativeTokenFallback) {
        String type = inferReceiverType(primary, nativeTokenFallback);
        if (type != null) return new Receiver(type, false);

        PsiElement headId = QilletniPsiUtil.findDirectIdChild(primary);
        if (headId != null && QilletniIndexFacade.findEntityByTypeName(file.getProject(), file, headId.getText()) != null) {
            return new Receiver(headId.getText(), true);
        }
        return null;
    }

    private static @Nullable String nativeTypeName(PsiElement element) {
        return element.getNode() != null ? NATIVE_TYPE_NAMES.get(element.getNode().getElementType()) : null;
    }
}
