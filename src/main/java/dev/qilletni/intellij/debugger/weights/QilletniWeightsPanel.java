package dev.qilletni.intellij.debugger.weights;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DoubleClickListener;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.debugger.QilletniDebuggerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The debugger's Weights tab: each weights value picked from, as a tree of its entries with their chance and how
 * often they were actually picked. Repaints at most every 250 ms, however fast picks arrive.
 */
public final class QilletniWeightsPanel extends JPanel implements Disposable {

    /** Fetches every song of an entry, then refreshes the panel. */
    public interface SongLoader {
        void load(int weightsId, @NotNull String key);
    }

    private static final int REFRESH_MS = 250;
    private static final JBColor ACTUAL_BAR = new JBColor(0x6A9FE6, 0x4A7BC2);
    private static final JBColor EXPECTED_MARK = new JBColor(0x3A3A3A, 0xD0D0D0);

    private final QilletniWeightStats stats;
    private final SongLoader songLoader;
    private final MergingUpdateQueue queue;
    private final DefaultComboBoxModel<Integer> weightsModel = new DefaultComboBoxModel<>();
    private final ComboBox<Integer> weightsCombo = new ComboBox<>(weightsModel);
    private final JBLabel picksLabel = new JBLabel();
    private final JBLabel lastPickLabel = new JBLabel();
    private final JBLabel lastRejectedLabel = new JBLabel();
    private final ListTreeTableModelOnColumns model;
    private final TreeTableView table;

    private Map<Integer, QilletniWeightStats.Summary> summaries = Map.of();
    /** Expanded rows by weights id, so switching weights and refreshing keep what the user opened. */
    private final Map<Integer, Set<String>> expanded = new HashMap<>();
    private int shownId;
    /** Rows to expand once their songs arrive. */
    private final Set<String> expandWhenLoaded = new HashSet<>();
    /** Show the most recently picked weights until the user chooses another. */
    private boolean followLatest = true;
    private boolean updatingCombo;

    public QilletniWeightsPanel(@NotNull QilletniWeightStats stats, @NotNull SongLoader songLoader) {
        super(new BorderLayout());
        this.stats = stats;
        this.songLoader = songLoader;
        this.queue = new MergingUpdateQueue("Qilletni weights", REFRESH_MS, true, null, this, null, true);

        var right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        ColumnInfo<?, ?>[] columns = {
                new TreeColumnInfo(QilletniBundle.message("debugger.weights.column.entry")),
                new TextColumn("debugger.weights.column.chance", 70, right, QilletniWeightsPanel::chanceText),
                new ActualColumn(),
                new TextColumn("debugger.weights.column.picks", 60, right, row -> isCounted(row) ? QilletniWeightStats.count(row.count()) : ""),
                new TextColumn("debugger.weights.column.rejected", 50, right, row -> row.rejected() > 0 ? QilletniWeightStats.count(row.rejected()) : "")};

        model = new ListTreeTableModelOnColumns(new DefaultMutableTreeNode(), columns);
        table = new TreeTableView(model) {
            @Override
            public String getToolTipText(@NotNull MouseEvent event) {
                var tableRow = rowAtPoint(event.getPoint());
                var path = tableRow < 0 ? null : getTree().getPathForRow(tableRow);
                var row = path == null ? null : rowOf(path.getLastPathComponent());
                return row == null ? null : tooltip(row);
            }
        };
        table.setRootVisible(false);
        table.setTreeCellRenderer(new EntryRenderer());
        table.getEmptyText().setText(QilletniBundle.message("debugger.weights.empty"));
        explainColumns();

        weightsCombo.setRenderer(SimpleListCellRenderer.create("", id -> {
            var summary = summaries.get(id);
            return summary == null ? "" : QilletniBundle.message("debugger.weights.choice", summary.label(), QilletniWeightStats.count(summary.total()));
        }));
        weightsCombo.addActionListener(e -> {
            if (updatingCombo || weightsCombo.getSelectedItem() == null) return;
            followLatest = weightsCombo.getSelectedItem().equals(stats.latestId());
            refresh();
        });

        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                var row = selectedRow();
                if (row == null || !row.canShowSongs()) return false;
                showSongs(row);
                return true;
            }
        }.installOn(table);

        var toolbar = ActionManager.getInstance().createActionToolbar("QilletniWeights", new DefaultActionGroup(new ShowSongsAction()), true);
        toolbar.setTargetComponent(this);

        var top = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)));
        top.add(weightsCombo);
        top.add(toolbar.getComponent());
        top.add(picksLabel);

        var footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(JBUI.Borders.empty(4, 8));
        lastPickLabel.setCopyable(true);
        lastRejectedLabel.setCopyable(true);
        footer.add(lastPickLabel);
        footer.add(lastRejectedLabel);

        add(top, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        refresh();
    }

    /** Explains each column in a tooltip on its header. */
    private void explainColumns() {
        var header = table.getTableHeader();
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                var column = header.columnAtPoint(event.getPoint());
                var key = switch (column < 0 ? -1 : header.getColumnModel().getColumn(column).getModelIndex()) {
                    case 1 -> "debugger.weights.column.chance.tooltip";
                    case 2 -> "debugger.weights.column.actual.tooltip";
                    case 3 -> "debugger.weights.column.picks.tooltip";
                    case 4 -> "debugger.weights.column.rejected.tooltip";
                    default -> null;
                };
                header.setToolTipText(key == null ? null : QilletniBundle.message(key));
            }
        });
    }

    /** Schedules a repaint from the latest stats. Safe from any thread. */
    public void refreshLater() {
        queue.queue(Update.create("refresh", this::refresh));
    }

    @Override
    public void dispose() {
    }

    private void refresh() {
        var all = stats.summaries();
        var latest = stats.latestId();
        updateCombo(all, latest);

        var selected = weightsCombo.getSelectedItem();
        var view = selected instanceof Integer id ? stats.view(id) : null;
        if (view == null) {
            picksLabel.setText("");
            lastPickLabel.setText("");
            lastRejectedLabel.setText("");
            return;
        }

        picksLabel.setText(QilletniBundle.message("debugger.weights.picks", QilletniWeightStats.count(view.total()), QilletniWeightStats.count(view.misses())));
        lastPickLabel.setText(view.lastPick() == null ? "" : QilletniBundle.message("debugger.weights.lastPick", view.lastPick()));
        lastRejectedLabel.setText(view.lastRejected().isEmpty() ? "" : QilletniBundle.message("debugger.weights.lastRejected", String.join("; ", view.lastRejected())));
        showRows(view);
    }

    private void updateCombo(List<QilletniWeightStats.Summary> all, int latest) {
        var byId = new HashMap<Integer, QilletniWeightStats.Summary>();
        all.forEach(summary -> byId.put(summary.id(), summary));
        summaries = byId;

        updatingCombo = true;
        try {
            for (var summary : all) {
                if (weightsModel.getIndexOf(summary.id()) < 0) weightsModel.addElement(summary.id());
            }
            if (latest != 0 && (followLatest || weightsCombo.getSelectedItem() == null)) {
                weightsCombo.setSelectedItem(latest);
            }
        } finally {
            updatingCombo = false;
        }
        weightsCombo.repaint();
    }

    /** Replaces the rows, keeping which ones are expanded and selected. */
    private void showRows(QilletniWeightStats.View view) {
        var selectedKey = shownId == view.id() ? selectedKey() : null;
        if (shownId != 0) {
            expanded.put(shownId, expandedKeys());
        }

        var root = new DefaultMutableTreeNode();
        view.rows().forEach(row -> root.add(node(row)));
        model.setRoot(root);

        var keys = expanded.computeIfAbsent(view.id(), id -> defaultExpanded(view.rows(), new HashSet<>()));
        expandWhenLoaded.removeIf(key -> {
            var row = find(view.rows(), key);
            if (row == null || !row.songsShown()) return false;
            keys.add(key);
            return true;
        });
        expand(root, keys);
        if (selectedKey != null) select(root, selectedKey);
        shownId = view.id();
    }

    /**
     * Fetches every song of {@code row}, after asking first if there are more than the setting allows automatically.
     */
    private void showSongs(QilletniWeightStats.Row row) {
        var limit = QilletniDebuggerSettings.getInstance().getWeightSongLimit();
        if (row.songCount() > limit && Messages.showYesNoDialog(this,
                QilletniBundle.message("debugger.weights.showSongs.confirm", QilletniWeightStats.count(row.songCount()), row.text()),
                QilletniBundle.message("debugger.weights.showSongs"), Messages.getQuestionIcon()) != Messages.YES) {
            return;
        }

        expandWhenLoaded.add(row.key());
        songLoader.load(shownId, row.key());
    }

    private @Nullable QilletniWeightStats.Row selectedRow() {
        var path = table.getTree().getSelectionPath();
        return path == null ? null : rowOf(path.getLastPathComponent());
    }

    private static @Nullable QilletniWeightStats.Row find(List<QilletniWeightStats.Row> rows, String key) {
        for (var row : rows) {
            if (row.key().equals(key)) return row;
            var child = find(row.children(), key);
            if (child != null) return child;
        }
        return null;
    }

    private final class ShowSongsAction extends DumbAwareAction {
        ShowSongsAction() {
            super(QilletniBundle.message("debugger.weights.showSongs"), QilletniBundle.message("debugger.weights.showSongs.description"),
                    AllIcons.Actions.ShowAsTree);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            var row = selectedRow();
            event.getPresentation().setEnabled(row != null && row.canShowSongs());
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            var row = selectedRow();
            if (row != null && row.canShowSongs()) showSongs(row);
        }
    }

    private @Nullable String selectedKey() {
        var path = table.getTree().getSelectionPath();
        var row = path == null ? null : rowOf(path.getLastPathComponent());
        return row == null ? null : row.key();
    }

    private void select(DefaultMutableTreeNode root, String key) {
        var nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            var node = (DefaultMutableTreeNode) nodes.nextElement();
            var row = rowOf(node);
            if (row != null && row.key().equals(key)) {
                table.getTree().setSelectionPath(new TreePath(node.getPath()));
                return;
            }
        }
    }

    private Set<String> expandedKeys() {
        var keys = new HashSet<String>();
        var tree = table.getTree();
        for (int i = 0; i < tree.getRowCount(); i++) {
            var path = tree.getPathForRow(i);
            var row = rowOf(path.getLastPathComponent());
            if (row != null && tree.isExpanded(path)) keys.add(row.key());
        }
        return keys;
    }

    /** Nested weights start expanded; song lists under an entry start collapsed. */
    private static Set<String> defaultExpanded(List<QilletniWeightStats.Row> rows, Set<String> keys) {
        for (var row : rows) {
            if (row.children().stream().anyMatch(child -> child.kind() == QilletniWeightStats.Kind.ENTRY)) {
                keys.add(row.key());
                defaultExpanded(row.children(), keys);
            }
        }
        return keys;
    }

    private void expand(DefaultMutableTreeNode parent, Set<String> keys) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            var child = (DefaultMutableTreeNode) parent.getChildAt(i);
            var row = rowOf(child);
            if (row != null && keys.contains(row.key())) {
                table.getTree().expandPath(new TreePath(child.getPath()));
                expand(child, keys);
            }
        }
    }

    private static DefaultMutableTreeNode node(QilletniWeightStats.Row row) {
        var node = new DefaultMutableTreeNode(row);
        row.children().forEach(child -> node.add(node(child)));
        return node;
    }

    private static @Nullable QilletniWeightStats.Row rowOf(Object value) {
        return value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof QilletniWeightStats.Row row ? row : null;
    }

    private static boolean isCounted(QilletniWeightStats.Row row) {
        return row.kind() != QilletniWeightStats.Kind.MULTIPLIER && row.kind() != QilletniWeightStats.Kind.OMITTED;
    }

    private static String chanceText(QilletniWeightStats.Row row) {
        if (row.kind() == QilletniWeightStats.Kind.MULTIPLIER) return "—";
        return Double.isNaN(row.expected()) ? "" : QilletniWeightStats.percent(row.expected());
    }

    private static String tooltip(QilletniWeightStats.Row row) {
        if (!isCounted(row)) return row.tooltip();
        var parts = new ArrayList<String>();
        if (!Double.isNaN(row.expected())) {
            parts.add(QilletniBundle.message("debugger.weights.tooltip.chance", QilletniWeightStats.percent(row.expected())));
        }
        parts.add(QilletniBundle.message("debugger.weights.tooltip.actual", QilletniWeightStats.percent(row.actual()), QilletniWeightStats.count(row.count())));
        if (row.rejected() > 0) {
            parts.add(QilletniBundle.message("debugger.weights.tooltip.rejected", QilletniWeightStats.count(row.rejected())));
        }
        return row.tooltip() + " — " + String.join(", ", parts);
    }

    private static final class EntryRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            var entry = rowOf(value);
            if (entry == null) return;

            if (entry.separator() != null) append(entry.separator() + " ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            var attributes = switch (entry.kind()) {
                case OTHERS, OMITTED, MULTIPLIER, MISS -> SimpleTextAttributes.GRAYED_ATTRIBUTES;
                default -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
            };
            append(entry.text(), attributes);
        }
    }

    /** A right-aligned text column with a fixed width. */
    private static final class TextColumn extends ColumnInfo<Object, String> {
        private final int width;
        private final TableCellRenderer renderer;
        private final Function<QilletniWeightStats.Row, String> text;

        TextColumn(String nameKey, int width, TableCellRenderer renderer, Function<QilletniWeightStats.Row, String> text) {
            super(QilletniBundle.message(nameKey));
            this.width = width;
            this.renderer = renderer;
            this.text = text;
        }

        @Override
        public @Nullable String valueOf(Object item) {
            var row = rowOf(item);
            return row == null ? "" : text.apply(row);
        }

        @Override
        public @NotNull TableCellRenderer getRenderer(Object item) {
            return renderer;
        }

        @Override
        public int getWidth(JTable table) {
            return JBUI.scale(width);
        }
    }

    private static final class ActualColumn extends ColumnInfo<Object, QilletniWeightStats.Row> {
        private final BarRenderer renderer = new BarRenderer();

        ActualColumn() {
            super(QilletniBundle.message("debugger.weights.column.actual"));
        }

        @Override
        public @Nullable QilletniWeightStats.Row valueOf(Object item) {
            return rowOf(item);
        }

        @Override
        public @NotNull TableCellRenderer getRenderer(Object item) {
            return renderer;
        }

        @Override
        public int getWidth(JTable table) {
            return JBUI.scale(BarRenderer.BAR_WIDTH + 70);
        }
    }

    /**
     * A bar of the row's share of picks, with a mark at its expected chance, then the share as text.
     */
    private static final class BarRenderer extends JComponent implements TableCellRenderer {
        private static final int BAR_WIDTH = 100;

        private QilletniWeightStats.@Nullable Row row;
        private boolean selected;
        private JTable table;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int r, int column) {
            this.table = table;
            this.row = value instanceof QilletniWeightStats.Row entry ? entry : null;
            this.selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setColor(selected ? table.getSelectionBackground() : table.getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                if (row == null || !isCounted(row)) return;

                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                var x = JBUI.scale(4);
                var width = JBUI.scale(BAR_WIDTH);
                var height = Math.max(JBUI.scale(6), getHeight() / 2);
                var y = (getHeight() - height) / 2;

                g.setColor(JBColor.border());
                g.drawRect(x, y, width, height);
                g.setColor(ACTUAL_BAR);
                g.fillRect(x + 1, y + 1, (int) Math.round(Math.min(100, row.actual()) / 100 * (width - 1)), height - 1);

                if (!Double.isNaN(row.expected())) {
                    var markX = x + (int) Math.round(Math.min(100, row.expected()) / 100 * width);
                    g.setColor(EXPECTED_MARK);
                    g.fillRect(markX - JBUI.scale(1), y - JBUI.scale(2), JBUI.scale(2), height + JBUI.scale(4));
                }

                var text = QilletniWeightStats.percent(row.actual());
                g.setFont(table.getFont());
                g.setColor(selected ? table.getSelectionForeground() : UIUtil.getLabelForeground());
                var metrics = g.getFontMetrics();
                var textX = getWidth() - JBUI.scale(4) - metrics.stringWidth(text);
                g.drawString(text, Math.max(x + width + JBUI.scale(6), textX), (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            } finally {
                g.dispose();
            }
        }
    }
}
