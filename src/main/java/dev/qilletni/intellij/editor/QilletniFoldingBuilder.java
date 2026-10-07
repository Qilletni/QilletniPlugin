package dev.qilletni.intellij.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.psi.QilletniImportFile;
import dev.qilletni.intellij.psi.QilletniTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Folds brace blocks, multi-line comments and runs of imports. */
public final class QilletniFoldingBuilder extends FoldingBuilderEx implements DumbAware {

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        var descriptors = new ArrayList<FoldingDescriptor>();
        collect(root.getNode(), document, descriptors);
        foldImports(root, document, descriptors);
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static void collect(ASTNode node, Document document, List<FoldingDescriptor> out) {
        var type = node.getElementType();
        if (type == QilletniTypes.BLOCK_COMMENT || type == QilletniTypes.DOC_COMMENT) {
            addIfMultiline(node, node.getTextRange(), document, out);
        } else if (type == QilletniTypes.LEFT_CBRACKET) {
            // The matching brace is the last RIGHT_CBRACKET among the siblings (every braced rule ends with one)
            ASTNode close = null;
            for (var sibling = node.getTreeNext(); sibling != null; sibling = sibling.getTreeNext()) {
                if (sibling.getElementType() == QilletniTypes.RIGHT_CBRACKET) close = sibling;
            }
            if (close != null) {
                addIfMultiline(node, new TextRange(node.getStartOffset(), close.getTextRange().getEndOffset()), document, out);
            }
        }
        for (var child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            collect(child, document, out);
        }
    }

    private static void foldImports(PsiElement root, Document document, List<FoldingDescriptor> out) {
        var imports = List.copyOf(PsiTreeUtil.findChildrenOfType(root, QilletniImportFile.class));
        if (imports.size() < 2) return;
        var first = imports.getFirst();
        var last = imports.getLast();
        // Skip the "import" keyword so the folded text still starts with it
        int start = first.getTextRange().getStartOffset() + "import ".length();
        if (start < last.getTextRange().getEndOffset()) {
            addIfMultiline(first.getNode(), new TextRange(start, last.getTextRange().getEndOffset()), document, out);
        }
    }

    private static void addIfMultiline(ASTNode node, TextRange range, Document document, List<FoldingDescriptor> out) {
        if (range.getLength() < 2) return;
        if (document.getLineNumber(range.getStartOffset()) == document.getLineNumber(range.getEndOffset())) return;
        out.add(new FoldingDescriptor(node, range));
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        var type = node.getElementType();
        if (type == QilletniTypes.DOC_COMMENT) return "/** ... */";
        if (type == QilletniTypes.BLOCK_COMMENT) return "/* ... */";
        if (node.getPsi() instanceof QilletniImportFile) return "...";
        return "{...}";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return node.getPsi() instanceof QilletniImportFile;
    }
}
