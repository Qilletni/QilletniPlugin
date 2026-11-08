package dev.qilletni.intellij.debugger.simulator;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import dev.qilletni.intellij.debugger.weights.EntrySpan;
import dev.qilletni.intellij.debugger.weights.QilletniWeightStats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Marks the simulated weights in the editor: each entry's chance at the end of its line, and boxes around the entries
 * of the latest real pick, the hovered pick and the clicked pick. Shown only while the Weights Simulator is visible.
 */
final class SimulatorEditorDecorations implements Disposable {

    /** Box layers, drawn in this order; each has its own color. */
    enum Box {
        LATEST(new JBColor(0xD08A3B, 0xE0A458)),
        HOVER(new JBColor(0x8A9099, 0xA4AAB3)),
        PINNED(new JBColor(0x3D7BD9, 0x6EA0F0));

        final JBColor color;

        Box(JBColor color) {
            this.color = color;
        }
    }

    private final Project project;
    private @Nullable QilletniSimulatorService.Source source;
    private List<QilletniWeightSimulator.Entry> roots = List.of();
    private boolean visible;
    private final Map<Box, List<Integer>> boxed = new EnumMap<>(Box.class);

    private final List<Inlay<?>> inlays = new ArrayList<>();
    private @Nullable Object inlaysFor;
    private final Map<Box, List<RangeHighlighter>> highlighters = new EnumMap<>(Box.class);
    private final Map<Box, Object> highlightersFor = new EnumMap<>(Box.class);

    SimulatorEditorDecorations(@NotNull Project project) {
        this.project = project;
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager manager, @NotNull VirtualFile file) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    clear();
                    apply();
                }, project.getDisposed());
            }
        });
    }

    /** The weights shown in the simulator, and the entry indices of the latest real pick while debugging. */
    void show(@Nullable QilletniSimulatorService.Source source, @NotNull List<QilletniWeightSimulator.Entry> roots, @Nullable List<Integer> latest) {
        this.source = source;
        this.roots = roots;
        setBox(Box.LATEST, latest);
        apply();
    }

    void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        if (!visible) clear();
        apply();
    }

    void setBox(@NotNull Box box, @Nullable List<Integer> indices) {
        if (indices == null) boxed.remove(box);
        else boxed.put(box, indices);
        apply();
    }

    @Nullable List<Integer> box(@NotNull Box box) {
        return boxed.get(box);
    }

    /** Opens the file of the entry at {@code indices} and scrolls to it, keeping focus in the simulator. */
    void navigate(@NotNull List<Integer> indices) {
        var path = path(indices);
        if (path.isEmpty()) return;
        var span = path.getLast().span();
        var file = span == null ? null : file(span);
        var document = file == null ? null : FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return;
        new OpenFileDescriptor(project, file, offset(document, span.line(), span.column())).navigate(false);
    }

    @Override
    public void dispose() {
        clear();
    }

    private void apply() {
        var inlayKey = visible ? List.of(roots, Objects.requireNonNullElse(source, "")) : null;
        if (!Objects.equals(inlayKey, inlaysFor)) {
            inlays.forEach(Inlay::dispose);
            inlays.clear();
            inlaysFor = inlayKey;
            if (visible) addInlays();
        }

        for (var box : Box.values()) {
            var indices = visible ? boxed.get(box) : null;
            var key = indices == null ? null : List.of(roots, indices);
            if (Objects.equals(key, highlightersFor.get(box))) continue;
            highlighters.getOrDefault(box, List.of()).forEach(RangeHighlighter::dispose);
            highlighters.remove(box);
            highlightersFor.remove(box);
            if (indices != null) {
                highlighters.put(box, addBoxes(box, path(indices)));
                highlightersFor.put(box, key);
            }
        }
    }

    private void clear() {
        inlays.forEach(Inlay::dispose);
        inlays.clear();
        inlaysFor = null;
        highlighters.values().forEach(list -> list.forEach(RangeHighlighter::dispose));
        highlighters.clear();
        highlightersFor.clear();
    }

    /** One chance per entry line; entries written once but reached through several paths list each chance. */
    private void addInlays() {
        var texts = new LinkedHashMap<EntrySpan, List<String>>();
        collectChances(roots, texts);
        texts.forEach((span, chances) -> {
            var file = file(span);
            var document = file == null ? null : FileDocumentManager.getInstance().getDocument(file);
            if (document == null || span.line() > document.getLineCount()) return;
            var offset = document.getLineEndOffset(span.line() - 1);
            var renderer = new ChanceRenderer(String.join(" / ", chances));
            for (var editor : editors(document)) {
                var inlay = editor.getInlayModel().addAfterLineEndElement(offset, new InlayProperties().relatesToPrecedingText(true), renderer);
                if (inlay != null) inlays.add(inlay);
            }
        });
    }

    private static void collectChances(List<QilletniWeightSimulator.Entry> entries, Map<EntrySpan, List<String>> texts) {
        for (var entry : entries) {
            if (entry.span() != null) {
                var text = entry.multiplier() ? "queue copies" : QilletniWeightStats.percent(entry.combined());
                var list = texts.computeIfAbsent(entry.span(), span -> new ArrayList<>());
                if (!list.contains(text)) list.add(text);
            }
            collectChances(entry.children(), texts);
        }
    }

    private List<RangeHighlighter> addBoxes(Box box, List<QilletniWeightSimulator.Entry> path) {
        var attributes = new TextAttributes(null, null, box.color, EffectType.ROUNDED_BOX, Font.PLAIN);
        var added = new ArrayList<RangeHighlighter>();
        for (var entry : path) {
            var span = entry.span();
            var file = span == null ? null : file(span);
            var document = file == null ? null : FileDocumentManager.getInstance().getDocument(file);
            if (document == null || span.line() > document.getLineCount() || span.endLine() > document.getLineCount()) continue;
            var start = offset(document, span.line(), span.column());
            var end = Math.max(start, offset(document, span.endLine(), span.endColumn()));
            for (var editor : editors(document)) {
                added.add(editor.getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.SELECTION - 1 - box.ordinal(), attributes, HighlighterTargetArea.EXACT_RANGE));
            }
        }
        return added;
    }

    /** The entries at {@code indices}, outermost first, as far as they're known. */
    private List<QilletniWeightSimulator.Entry> path(List<Integer> indices) {
        var path = new ArrayList<QilletniWeightSimulator.Entry>();
        var entries = roots;
        for (var index : indices) {
            var entry = entries.stream().filter(e -> e.index() == index).findFirst().orElse(null);
            if (entry == null) break;
            path.add(entry);
            entries = entry.children();
        }
        return path;
    }

    private @Nullable VirtualFile file(EntrySpan span) {
        var current = source;
        return current == null ? null : current.file(span.path());
    }

    private Editor[] editors(Document document) {
        return EditorFactory.getInstance().getEditors(document, project);
    }

    private static int offset(Document document, int line, int column) {
        var lineIndex = Math.max(0, Math.min(line - 1, document.getLineCount() - 1));
        var start = document.getLineStartOffset(lineIndex);
        return Math.min(start + Math.max(0, column - 1), document.getLineEndOffset(lineIndex));
    }

    /** Grey italic text after the end of a line. */
    private static final class ChanceRenderer implements EditorCustomElementRenderer {
        private final String text;

        ChanceRenderer(String text) {
            this.text = text;
        }

        @Override
        public int calcWidthInPixels(@NotNull Inlay inlay) {
            var editor = inlay.getEditor();
            return editor.getContentComponent().getFontMetrics(font(editor)).stringWidth(text) + JBUI.scale(16);
        }

        @Override
        public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle target, @NotNull TextAttributes textAttributes) {
            var editor = inlay.getEditor();
            var attributes = editor.getColorsScheme().getAttributes(DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND);
            g.setFont(font(editor));
            g.setColor(attributes != null && attributes.getForegroundColor() != null ? attributes.getForegroundColor() : JBColor.GRAY);
            g.drawString(text, target.x + JBUI.scale(16), target.y + editor.getAscent());
        }

        private static Font font(Editor editor) {
            return editor.getColorsScheme().getFont(EditorFontType.ITALIC);
        }
    }
}
