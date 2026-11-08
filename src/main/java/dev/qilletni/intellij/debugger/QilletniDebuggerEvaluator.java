package dev.qilletni.intellij.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import dev.qilletni.intellij.debugger.dap.QilletniDapSession;
import dev.qilletni.intellij.debugger.frames.QilletniValue;
import dev.qilletni.intellij.psi.QilletniTypes;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Evaluates in one stack frame through DAP {@code evaluate}. Expressions are sent with context {@code watch} and
 * code fragments with {@code repl}. Hover values are offered only for plain names and property chains
 * ({@code a}, {@code a.b}), never function calls, so hovering can't run code.
 */
public final class QilletniDebuggerEvaluator extends XDebuggerEvaluator {
    private final QilletniDebugProcess process;
    private final int frameId;

    public QilletniDebuggerEvaluator(@NotNull QilletniDebugProcess process, int frameId) {
        this.process = process;
        this.frameId = frameId;
    }

    @Override
    public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition position) {
        evaluate(expression, EvaluateArgumentsContext.WATCH, callback);
    }

    @Override
    public void evaluate(@NotNull XExpression expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition position) {
        var context = expression.getMode() == EvaluationMode.CODE_FRAGMENT ? EvaluateArgumentsContext.REPL : EvaluateArgumentsContext.WATCH;
        evaluate(expression.getExpression(), context, callback);
    }

    private void evaluate(String expression, String context, XEvaluationCallback callback) {
        var args = new EvaluateArguments();
        args.setExpression(expression);
        args.setFrameId(frameId);
        args.setContext(context);
        process.dap().server().evaluate(args)
                .thenAccept(response -> callback.evaluated(QilletniValue.ofEvaluation(process, expression, response)))
                .exceptionally(error -> {
                    callback.errorOccurred(QilletniDapSession.messageOf(error));
                    return null;
                });
    }

    @Override
    public @Nullable TextRange getExpressionRangeAtOffset(Project project, Document document, int offset, boolean sideEffectsAllowed) {
        if (!sideEffectsAllowed && !process.supportsHoverEvaluation()) return null;
        var file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (file == null) return null;
        var leaf = file.findElementAt(offset);
        if (leaf == null || PsiUtilCore.getElementType(leaf) != QilletniTypes.ID) return null;
        return propertyChainRange(document.getCharsSequence(), leaf.getTextRange());
    }

    /**
     * Extends an identifier leftwards over {@code name.} segments, so hovering {@code b} in {@code a.b} evaluates
     * {@code a.b}. Returns null when the identifier is called ({@code f(}), or follows a call ({@code f().b}).
     */
    static @Nullable TextRange propertyChainRange(@NotNull CharSequence text, @NotNull TextRange identifier) {
        int end = identifier.getEndOffset();
        int after = end;
        while (after < text.length() && Character.isWhitespace(text.charAt(after))) after++;
        if (after < text.length() && text.charAt(after) == '(') return null;

        int start = identifier.getStartOffset();
        while (start > 0 && text.charAt(start - 1) == '.') {
            int segmentEnd = start - 1;
            int segmentStart = segmentEnd;
            while (segmentStart > 0 && isIdentifierPart(text.charAt(segmentStart - 1))) segmentStart--;
            if (segmentStart == segmentEnd || !Character.isJavaIdentifierStart(text.charAt(segmentStart))) return null;
            start = segmentStart;
        }
        if (start > 0 && text.charAt(start - 1) == ')') return null;
        return new TextRange(start, end);
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
