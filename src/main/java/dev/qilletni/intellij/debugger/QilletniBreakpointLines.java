package dev.qilletni.intellij.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import dev.qilletni.intellij.psi.QilletniBodyStmt;
import dev.qilletni.intellij.psi.QilletniReturnStmt;
import org.jetbrains.annotations.NotNull;

/**
 * Which lines can hold a line breakpoint. The interpreter can only stop where a statement starts, so a line is
 * breakable when a non-comment {@code body_stmt} (other than an entity definition) or a {@code return_stmt} starts on
 * it. The toolchain's adapter applies the same rule when it verifies breakpoints.
 */
public final class QilletniBreakpointLines {
    private QilletniBreakpointLines() {}

    public static boolean isBreakable(@NotNull PsiFile file, @NotNull Document document, int line) {
        if (line < 0 || line >= document.getLineCount()) return false;
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        int offset = start;
        while (offset < end) {
            var leaf = file.findElementAt(offset);
            if (leaf == null) break;
            if (!(leaf instanceof PsiWhiteSpace) && startsBreakableStatement(leaf, start)) return true;
            offset = Math.max(leaf.getTextRange().getEndOffset(), offset + 1);
        }
        return false;
    }

    /** Whether {@code leaf} or one of its ancestors that starts at or after {@code lineStart} is a breakable statement. */
    private static boolean startsBreakableStatement(PsiElement leaf, int lineStart) {
        for (PsiElement e = leaf; e != null && !(e instanceof PsiFile); e = e.getParent()) {
            if (e.getTextRange().getStartOffset() < lineStart) return false;
            if (e instanceof QilletniReturnStmt) return true;
            if (e instanceof QilletniBodyStmt stmt && isExecutable(stmt)) return true;
        }
        return false;
    }

    private static boolean isExecutable(QilletniBodyStmt stmt) {
        if (stmt.getIfStmt() != null || stmt.getForStmt() != null || stmt.getExpr() != null) return true;
        return stmt.getStmt() != null && stmt.getStmt().getEntityDef() == null;
    }
}
