package dev.qilletni.intellij;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.psi.QilletniAsmt;
import dev.qilletni.intellij.psi.QilletniSingleWeight;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.psi.QilletniVarDeclaration;
import dev.qilletni.intellij.psi.QilletniWeightsExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Smart Enter handler for Qilletni weight expressions.
 * <p>
 * When the user presses Enter at the end of a weight line (e.g., "| 50% "God Knows" by "Knocked Loose""),
 * this handler automatically inserts a new "|" at the same indentation level.
 * <p>
 * If the user presses Enter again immediately after the inserted "|", it removes the pipe and
 * continues with normal line behavior.
 */
public class QilletniEnterHandler implements EnterHandlerDelegate {

    /**
     * State information for tracking pipe insertions to enable double-enter removal.
     */
    private static class PipeInsertionState {
        final long timestamp;
        final int line;
        final int indentLevel;

        PipeInsertionState(long timestamp, int line, int indentLevel) {
            this.timestamp = timestamp;
            this.line = line;
            this.indentLevel = indentLevel;
        }
    }

    private static final Key<PipeInsertionState> LAST_PIPE_INSERTION_KEY = Key.create("qilletni.lastPipeInsertion");

    @Override
    public Result preprocessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull Ref<Integer> caretOffset,
                                  @NotNull Ref<Integer> caretAdvance, @NotNull DataContext dataContext,
                                  @Nullable EditorActionHandler originalHandler) {

        if (!(file instanceof QilletniFile)) {
            return Result.Continue;
        }

        Document document = editor.getDocument();
        int offset = caretOffset.get();

        // Check if we just inserted a pipe and the user is pressing Enter again
        if (shouldRemoveJustInsertedPipe(editor, document, offset)) {
            handleDoubleEnter(editor, document, offset, caretOffset, caretAdvance);
            return Result.Stop; // We've completely handled this Enter key
        }

        // Check if we're at the end of a weight line and should insert a new pipe
        if (shouldInsertNewPipe(file, editor, offset)) {
            insertNewWeightPipe(editor, document, offset);
            return Result.Stop; // We've handled the Enter key
        }

        return Result.Continue;
    }

    @Override
    public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
        // No post-processing needed for our use case
        return Result.Continue;
    }

    /**
     * Determines if we should remove a just-inserted pipe because the user pressed Enter twice.
     */
    private boolean shouldRemoveJustInsertedPipe(@NotNull Editor editor, @NotNull Document document, int offset) {
        // Get the state of the last pipe insertion
        PipeInsertionState lastInsertion = editor.getUserData(LAST_PIPE_INSERTION_KEY);
        if (lastInsertion == null) {
            return false;
        }

        // Check if the insertion was recent (within 2 seconds)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastInsertion.timestamp > 2000) {
            editor.putUserData(LAST_PIPE_INSERTION_KEY, null);
            return false;
        }

        // Check if we're on the correct line
        int currentLine = document.getLineNumber(offset);
        if (currentLine != lastInsertion.line) {
            return false;
        }

        // Validate cursor is at the expected position after the inserted pipe
        return isAtExpectedPipePosition(editor, document, offset, lastInsertion);
    }

    /**
     * Validates that the cursor is at the expected position after an inserted pipe.
     */
    private boolean isAtExpectedPipePosition(@NotNull Editor editor, @NotNull Document document, int offset, @NotNull PipeInsertionState state) {
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));

        // The expected cursor position is: line start + indentation + "| " (2 characters)
        int expectedCursorPos = lineStart + state.indentLevel + 2;

        if (offset != expectedCursorPos) {
            return false;
        }

        // Verify the line actually contains the pipe pattern we expect
        CharSequence text = document.getCharsSequence();
        int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));

        // Check if we have enough characters for the expected pattern
        int pipeStart = lineStart + state.indentLevel;
        if (pipeStart + 2 > lineEnd) {
            return false;
        }

        // Verify the pattern is exactly "| " at the expected position
        String actualPattern = text.subSequence(pipeStart, pipeStart + 2).toString();
        return "| ".equals(actualPattern);
    }

    /**
     * Finds the WEIGHTS_KEYWORD token within a variable declaration or assignment.
     */
    @Nullable
    private PsiElement findWeightsKeywordToken(@Nullable PsiElement parent) {
        if (parent == null) return null;

        for (PsiElement child : parent.getChildren()) {
            if (child.getNode() != null && child.getNode().getElementType() == QilletniTypes.WEIGHTS_KEYWORD) {
                return child;
            }
        }
        return null;
    }

    /**
     * Calculates the indentation level of a PSI element.
     */
    private int calculateTokenIndentation(@NotNull PsiElement element, @NotNull Document document) {
        int elementOffset = element.getTextOffset();
        int lineStart = document.getLineStartOffset(document.getLineNumber(elementOffset));
        return elementOffset - lineStart;
    }

    /**
     * Finds the indentation level of the actual WEIGHTS_KEYWORD token (not the pipe).
     */
    private int findWeightsKeywordIndentation(@NotNull PsiFile file, @NotNull Editor editor, int offset) {
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());

        // Find the PSI element at the current position
        PsiElement element = file.findElementAt(offset - 1);
        if (element == null) return 0;

        Document document = editor.getDocument();

        // Look for variable declaration containing weights keyword (weights myWeights = ...)
        QilletniVarDeclaration varDecl = PsiTreeUtil.getParentOfType(element, QilletniVarDeclaration.class);
        if (varDecl != null) {
            PsiElement weightsKeyword = findWeightsKeywordToken(varDecl);
            if (weightsKeyword != null) {
                return calculateTokenIndentation(weightsKeyword, document);
            }
        }

        // Also check for assignment (myWeights = weights_expr)
        QilletniAsmt assignment = PsiTreeUtil.getParentOfType(element, QilletniAsmt.class);
        if (assignment != null) {
            PsiElement weightsKeyword = findWeightsKeywordToken(assignment);
            if (weightsKeyword != null) {
                return calculateTokenIndentation(weightsKeyword, document);
            }
        }

        return 0; // fallback to column 0 if weights keyword not found
    }

    /**
     * Handles the double-enter scenario by removing the pipe and positioning the caret correctly.
     */
    private void handleDoubleEnter(@NotNull Editor editor, @NotNull Document document, int offset,
                                   @NotNull Ref<Integer> caretOffset, @NotNull Ref<Integer> caretAdvance) {
        PipeInsertionState state = editor.getUserData(LAST_PIPE_INSERTION_KEY);
        if (state == null) return;

        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        int pipeStart = lineStart + state.indentLevel;

        // Remove the "| " at the expected position
        document.deleteString(pipeStart, pipeStart + 2);

        // Find the indentation level of the actual weights keyword token
        PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document);
        int weightsIndentLevel = file != null ? findWeightsKeywordIndentation(file, editor, offset) : 0;

        // Calculate the new caret position: at the weights keyword indentation level
        int newCaretPos = lineStart + weightsIndentLevel;

        // Update caret parameters to position cursor correctly
        caretOffset.set(newCaretPos);
        caretAdvance.set(0); // No additional advance needed

        // Clear the insertion state
        editor.putUserData(LAST_PIPE_INSERTION_KEY, null);
    }

    /**
     * Determines if we should insert a new weight pipe.
     */
    private boolean shouldInsertNewPipe(@NotNull PsiFile file, @NotNull Editor editor, int offset) {
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());

        // Find the PSI element at the current position
        PsiElement element = file.findElementAt(offset - 1); // Look at the character before cursor
        if (element == null) return false;

        // Check if we're inside a weights expression
        QilletniWeightsExpr weightsExpr = PsiTreeUtil.getParentOfType(element, QilletniWeightsExpr.class);
        if (weightsExpr == null) return false;

        // Check if we're at the end of a line that contains a weight pipe
        QilletniSingleWeight singleWeight = PsiTreeUtil.getParentOfType(element, QilletniSingleWeight.class);
        if (singleWeight == null) return false;

        // Verify we're actually at the end of the weight line
        Document document = editor.getDocument();
        int currentLine = document.getLineNumber(offset);
        int lineEnd = document.getLineEndOffset(currentLine);

        // Allow some whitespace at the end
        CharSequence text = document.getCharsSequence();
        String remainingText = text.subSequence(offset, lineEnd).toString().trim();

        return remainingText.isEmpty();
    }

    /**
     * Finds the WEIGHT_PIPE element within a single weight to determine proper indentation.
     */
    @Nullable
    private PsiElement findWeightPipeElement(@NotNull QilletniSingleWeight singleWeight) {
        PsiElement[] children = singleWeight.getChildren();
        for (PsiElement child : children) {
            if (child.getNode() != null && child.getNode().getElementType() == QilletniTypes.WEIGHT_PIPE) {
                return child;
            }
        }
        return null;
    }

    /**
     * Calculates the indentation level for a new weight pipe based on existing PSI structure.
     */
    private int calculateIndentationLevel(@NotNull PsiFile file, @NotNull Editor editor, int offset) {
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());

        // Find the PSI element at the current position
        PsiElement element = file.findElementAt(offset - 1);
        if (element == null) return 0;

        // Find the parent single weight
        QilletniSingleWeight singleWeight = PsiTreeUtil.getParentOfType(element, QilletniSingleWeight.class);
        if (singleWeight == null) return 0;

        // Find the WEIGHT_PIPE element within this single weight
        PsiElement pipeElement = findWeightPipeElement(singleWeight);
        if (pipeElement != null) {
            Document document = editor.getDocument();
            int pipeOffset = pipeElement.getTextOffset();
            int pipeLineStart = document.getLineStartOffset(document.getLineNumber(pipeOffset));
            return pipeOffset - pipeLineStart;
        }

        // Fallback: use current line indentation
        Document document = editor.getDocument();
        int currentLine = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(currentLine);
        CharSequence text = document.getCharsSequence();
        String lineText = text.subSequence(lineStart, document.getLineEndOffset(currentLine)).toString();

        for (int i = 0; i < lineText.length(); i++) {
            if (!Character.isWhitespace(lineText.charAt(i))) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Inserts a new weight pipe at the appropriate indentation level.
     */
    private void insertNewWeightPipe(@NotNull Editor editor, @NotNull Document document, int offset) {
        PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document);
        if (file == null) return;

        // Calculate the proper indentation level using PSI information
        int indentLevel = calculateIndentationLevel(file, editor, offset);

        // Create the indentation string
        String indentation = StringUtil.repeat(" ", indentLevel);

        // Insert newline + indentation + pipe + space
        String insertText = "\n" + indentation + "| ";
        document.insertString(offset, insertText);

        // Move cursor to after the pipe and space
        int newCursorPos = offset + insertText.length();
        editor.getCaretModel().moveToOffset(newCursorPos);

        // Record the complete insertion state
        int newLine = document.getLineNumber(newCursorPos);
        PipeInsertionState state = new PipeInsertionState(System.currentTimeMillis(), newLine, indentLevel);
        editor.putUserData(LAST_PIPE_INSERTION_KEY, state);
    }
}