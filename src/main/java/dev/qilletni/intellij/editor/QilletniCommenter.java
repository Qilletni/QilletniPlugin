package dev.qilletni.intellij.editor;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import dev.qilletni.intellij.psi.QilletniTypes;
import org.jetbrains.annotations.Nullable;

/** Comment toggling (Ctrl+/ and Ctrl+Shift+/) and doc-comment stubs for Qilletni. */
public final class QilletniCommenter implements CodeDocumentationAwareCommenter {
    @Override public @Nullable String getLineCommentPrefix() { return "//"; }
    @Override public @Nullable String getBlockCommentPrefix() { return "/*"; }
    @Override public @Nullable String getBlockCommentSuffix() { return "*/"; }
    @Override public @Nullable String getCommentedBlockCommentPrefix() { return null; }
    @Override public @Nullable String getCommentedBlockCommentSuffix() { return null; }

    @Override public @Nullable IElementType getLineCommentTokenType() { return QilletniTypes.LINE_COMMENT; }
    @Override public @Nullable IElementType getBlockCommentTokenType() { return QilletniTypes.BLOCK_COMMENT; }
    @Override public @Nullable IElementType getDocumentationCommentTokenType() { return QilletniTypes.DOC_COMMENT; }
    @Override public @Nullable String getDocumentationCommentPrefix() { return "/**"; }
    @Override public @Nullable String getDocumentationCommentLinePrefix() { return "*"; }
    @Override public @Nullable String getDocumentationCommentSuffix() { return "*/"; }

    @Override
    public boolean isDocumentationComment(PsiComment element) {
        return element.getNode().getElementType() == QilletniTypes.DOC_COMMENT;
    }
}
