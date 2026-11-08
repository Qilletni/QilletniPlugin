package dev.qilletni.intellij.debugger.weights;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Draws a rounded box around weight entries in every editor showing them, including editors opened later. Use on the
 * EDT.
 */
public final class EntryBoxes implements Disposable {
    /** The box for the entries of a stop or the latest pick. */
    public static final JBColor PICKED = new JBColor(0xD08A3B, 0xE0A458);
    /** The box for entries the user selected. */
    public static final JBColor SELECTED = new JBColor(0x3D7BD9, 0x6EA0F0);

    private final Project project;
    private final Function<String, @Nullable VirtualFile> files;
    private final TextAttributes attributes;
    private final int layer;
    private final List<RangeHighlighter> highlighters = new ArrayList<>();
    private List<EntrySpan> shown = List.of();

    /**
     * @param files Finds the file of a DAP source path
     */
    public EntryBoxes(@NotNull Project project, @NotNull Function<String, @Nullable VirtualFile> files, @NotNull JBColor color) {
        this.project = project;
        this.files = files;
        this.attributes = new TextAttributes(null, null, color, EffectType.ROUNDED_BOX, Font.PLAIN);
        this.layer = HighlighterLayer.SELECTION - (color == PICKED ? 1 : 2);
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager manager, @NotNull VirtualFile file) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    var spans = shown;
                    clear();
                    show(spans);
                }, project.getDisposed());
            }
        });
    }

    /** Boxes {@code spans}, replacing any boxes shown before. */
    public void show(@NotNull List<EntrySpan> spans) {
        if (spans.equals(shown) && !highlighters.isEmpty()) return;
        clear();
        shown = List.copyOf(spans);
        for (var span : spans) {
            var file = files.apply(span.path());
            var document = file == null ? null : FileDocumentManager.getInstance().getDocument(file);
            if (document == null || span.line() > document.getLineCount() || span.endLine() > document.getLineCount()) continue;
            var start = offset(document, span.line(), span.column());
            var end = Math.max(start, offset(document, span.endLine(), span.endColumn()));
            for (var editor : EditorFactory.getInstance().getEditors(document, project)) {
                highlighters.add(editor.getMarkupModel().addRangeHighlighter(start, end, layer, attributes, HighlighterTargetArea.EXACT_RANGE));
            }
        }
    }

    public void clear() {
        highlighters.forEach(RangeHighlighter::dispose);
        highlighters.clear();
        shown = List.of();
    }

    @Override
    public void dispose() {
        clear();
    }

    /** @return The offset of a 1-based line and column, kept inside the document */
    public static int offset(@NotNull Document document, int line, int column) {
        var lineIndex = Math.max(0, Math.min(line - 1, document.getLineCount() - 1));
        var start = document.getLineStartOffset(lineIndex);
        return Math.min(start + Math.max(0, column - 1), document.getLineEndOffset(lineIndex));
    }
}
