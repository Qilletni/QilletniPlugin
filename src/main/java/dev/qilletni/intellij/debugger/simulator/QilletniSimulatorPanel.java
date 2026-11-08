package dev.qilletni.intellij.debugger.simulator;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WrapLayout;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.debugger.weights.QilletniWeightStats;
import dev.qilletni.intellij.debugger.weights.SongLoadConfirmation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * The Weights Simulator: simulated picks of one weights value from the latest debug session, as bands colored by entry
 * and by the songs of one focused entry, beside the session's real picks and a mix of chances.
 */
final class QilletniSimulatorPanel extends JPanel implements Disposable {
    private static final int BATCH = 20;
    private static final int LONG_RUN_PICKS = 50_000;
    private static final int SHOWN_REROLLS = 3;
    private static final String EMPTY_CARD = "empty";
    private static final String CONTENT_CARD = "content";

    /** One weights value's simulation, kept until Recalculate or until its breakdown changes. */
    private static final class Simulation {
        QilletniWeightStats.SimulationInput input;
        QilletniWeightSimulator simulator;
        QilletniWeightSimulator.State state;
        final List<QilletniWeightSimulator.SimPick> picks = new ArrayList<>();
        boolean blocked;
        double @Nullable [] longRun;
        @Nullable Object longRunFor;
        int focus;
    }

    private final Project project;
    private final Random random = new Random();
    private final MergingUpdateQueue queue;
    private final Map<Integer, Simulation> simulations = new HashMap<>();

    private final CardLayout cards = new CardLayout();
    private final JBLabel emptyLabel = new JBLabel("", SwingConstants.CENTER);
    private final DefaultComboBoxModel<Integer> weightsModel = new DefaultComboBoxModel<>();
    private final ComboBox<Integer> weightsCombo = new ComboBox<>(weightsModel);
    private final JBLabel countLabel = new JBLabel();
    private final JBLabel blockedLabel = new JBLabel();
    private final JBLabel focusTitle = new JBLabel();
    private final JBLabel focusNote = new JBLabel();
    private final JButton loadSongs = new JButton(QilletniBundle.message("debugger.simulator.loadSongs"));
    private final StripComponent.Listener stripListener = new StripComponent.Listener() {
        @Override
        public void hovered(@Nullable List<Integer> indices) {
            decorations.setBox(SimulatorEditorDecorations.Box.HOVER, indices);
        }

        @Override
        public void clicked(@Nullable List<Integer> indices) {
            if (indices == null || indices.equals(decorations.box(SimulatorEditorDecorations.Box.PINNED))) {
                decorations.setBox(SimulatorEditorDecorations.Box.PINNED, null);
                return;
            }
            decorations.setBox(SimulatorEditorDecorations.Box.PINNED, indices);
            decorations.navigate(indices);
        }
    };
    /** Clicking a pick in All entries or This session also focuses its entry, like its legend button. */
    private final StripComponent.Listener focusingListener = new StripComponent.Listener() {
        @Override
        public void hovered(@Nullable List<Integer> indices) {
            stripListener.hovered(indices);
        }

        @Override
        public void clicked(@Nullable List<Integer> indices) {
            if (indices != null) focus(indices.getFirst());
            stripListener.clicked(indices);
        }
    };
    private final StripComponent allStrip = new StripComponent(focusingListener);
    private final StripComponent focusStrip = new StripComponent(stripListener);
    private final StripComponent realStrip = new StripComponent(focusingListener);
    private final JBCheckBox viewAll = new JBCheckBox(QilletniBundle.message("debugger.simulator.viewAll"));
    private final SimulatorEditorDecorations decorations;
    private final JPanel legend = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2)));
    private final MixBarsComponent mix = new MixBarsComponent();

    private Map<Integer, QilletniWeightStats.Summary> summaries = Map.of();
    private boolean followLatest = true;
    private boolean updatingCombo;

    QilletniSimulatorPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.queue = new MergingUpdateQueue("Qilletni weights simulator", 250, true, null, this, null, true);
        this.decorations = new SimulatorEditorDecorations(project);
        Disposer.register(this, decorations);
        project.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(@NotNull ToolWindowManager manager) {
                updateVisibility();
            }
        });

        var root = new JPanel(cards);
        emptyLabel.setForeground(UIUtil.getContextHelpForeground());
        root.add(emptyLabel, EMPTY_CARD);
        root.add(content(), CONTENT_CARD);
        add(root, BorderLayout.CENTER);

        QilletniSimulatorService.getInstance(project).addListener(new QilletniSimulatorService.Listener() {
            @Override
            public void changed() {
                queue.queue(Update.create("refresh", QilletniSimulatorPanel.this::refresh));
            }

            @Override
            public void show(int weightsId) {
                followLatest = false;
                decorations.setBox(SimulatorEditorDecorations.Box.PINNED, null);
                updatingCombo = true;
                try {
                    if (weightsModel.getIndexOf(weightsId) < 0) weightsModel.addElement(weightsId);
                    weightsCombo.setSelectedItem(weightsId);
                } finally {
                    updatingCombo = false;
                }
                refresh();
            }
        }, this);
        refresh();
    }

    @Override
    public void dispose() {
    }

    private JPanel content() {
        weightsCombo.setRenderer(SimpleListCellRenderer.create("", id -> {
            var summary = summaries.get(id);
            return summary == null ? "" : QilletniBundle.message("debugger.weights.choice", summary.label(), QilletniWeightStats.count(summary.total()));
        }));
        weightsCombo.addActionListener(e -> {
            if (updatingCombo || !(weightsCombo.getSelectedItem() instanceof Integer id)) return;
            followLatest = id == latestId();
            decorations.setBox(SimulatorEditorDecorations.Box.PINNED, null);
            refresh();
        });

        var recalculate = new JButton(QilletniBundle.message("debugger.simulator.recalculate"));
        recalculate.addActionListener(e -> {
            var simulation = shown();
            if (simulation == null) return;
            simulation.state = simulation.simulator.newState();
            simulation.picks.clear();
            simulation.blocked = false;
            extend(simulation);
            render(simulation);
        });
        var loadMore = new JButton(QilletniBundle.message("debugger.simulator.loadMore", BATCH));
        loadMore.addActionListener(e -> {
            var simulation = shown();
            if (simulation == null || simulation.blocked) return;
            extend(simulation);
            render(simulation);
        });
        loadSongs.addActionListener(e -> loadFocusSongs());
        viewAll.setToolTipText(QilletniBundle.message("debugger.simulator.viewAll.description"));
        viewAll.addActionListener(e -> {
            var simulation = shown();
            if (simulation != null) render(simulation);
        });
        blockedLabel.setForeground(UIUtil.getErrorForeground());

        var toolbar = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)));
        toolbar.add(weightsCombo);
        toolbar.add(recalculate);
        toolbar.add(loadMore);
        toolbar.add(countLabel);
        toolbar.add(blockedLabel);

        var focusHeader = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        focusTitle.setFont(focusTitle.getFont().deriveFont(java.awt.Font.BOLD));
        focusNote.setForeground(UIUtil.getContextHelpForeground());
        focusHeader.add(focusTitle);
        focusHeader.add(loadSongs);
        focusHeader.add(focusNote);

        var strips = new JPanel(new GridLayout(1, 3, JBUI.scale(14), 0));
        strips.add(column("debugger.simulator.column.all", allStrip, null));
        strips.add(column("debugger.simulator.column.focus", focusStrip, null));
        strips.add(column("debugger.simulator.column.session", realStrip, viewAll));

        var body = new TrackingPanel();
        body.setBorder(JBUI.Borders.empty(8, 12, 12, 12));
        body.add(focusHeader);
        body.add(strips);
        body.add(legend);
        body.add(mix);

        var content = new JPanel(new BorderLayout());
        content.add(toolbar, BorderLayout.NORTH);
        content.add(ScrollPaneFactory.createScrollPane(body, true), BorderLayout.CENTER);
        return content;
    }

    /**
     * @param extra Shown after the title, or {@code null}
     */
    private static JPanel column(String titleKey, StripComponent strip, @Nullable JComponent extra) {
        var title = new JBLabel(QilletniBundle.message(titleKey));
        title.setForeground(UIUtil.getContextHelpForeground());
        var header = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        header.setBorder(JBUI.Borders.emptyBottom(4));
        header.add(title);
        if (extra != null) header.add(extra);
        var column = new JPanel(new BorderLayout());
        column.add(header, BorderLayout.NORTH);
        var holder = new JPanel(new BorderLayout());
        holder.add(strip, BorderLayout.NORTH);
        column.add(holder, BorderLayout.CENTER);
        return column;
    }

    /** A panel that fits the scroll pane's width, so wrapping rows wrap instead of scrolling sideways. */
    private static final class TrackingPanel extends JPanel implements Scrollable {
        TrackingPanel() {
            super(new VerticalLayout(JBUI.scale(12)));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return JBUI.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private int latestId() {
        var source = QilletniSimulatorService.getInstance(project).source();
        return source == null ? 0 : source.stats().latestId();
    }

    private @Nullable Simulation shown() {
        return weightsCombo.getSelectedItem() instanceof Integer id ? simulations.get(id) : null;
    }

    private void refresh() {
        var source = QilletniSimulatorService.getInstance(project).source();
        if (source == null) {
            showEmpty("debugger.simulator.empty");
            return;
        }

        var stats = source.stats();
        var all = stats.summaries();
        if (all.isEmpty()) {
            showEmpty("debugger.simulator.empty");
            return;
        }

        summaries = all.stream().collect(Collectors.toMap(QilletniWeightStats.Summary::id, summary -> summary));
        updatingCombo = true;
        try {
            var ids = all.stream().map(QilletniWeightStats.Summary::id).toList();
            for (int i = weightsModel.getSize() - 1; i >= 0; i--) {
                if (!ids.contains(weightsModel.getElementAt(i))) weightsModel.removeElementAt(i);
            }
            ids.forEach(id -> {
                if (weightsModel.getIndexOf(id) < 0) weightsModel.addElement(id);
            });
            if (followLatest || weightsCombo.getSelectedItem() == null) weightsCombo.setSelectedItem(stats.latestId());
        } finally {
            updatingCombo = false;
        }
        weightsCombo.repaint();

        if (!(weightsCombo.getSelectedItem() instanceof Integer id)) return;
        var input = stats.simulationInput(id);
        if (input == null || input.breakdown() == null) {
            showEmpty("debugger.simulator.waiting");
            return;
        }

        var simulation = simulations.get(id);
        if (simulation == null || simulation.input.breakdown() != input.breakdown()) {
            simulation = new Simulation();
            simulation.simulator = new QilletniWeightSimulator(input, random);
            simulation.state = simulation.simulator.newState();
            simulation.input = input;
            simulations.put(id, simulation);
            extend(simulation);
        } else if (!sameSongs(simulation.input, input)) {
            simulation.simulator = new QilletniWeightSimulator(input, random);
            simulation.input = input;
        } else {
            simulation.input = input;
        }

        cards.show((JPanel) getComponent(0), CONTENT_CARD);
        updateLongRun(simulation);
        render(simulation);
    }

    private void updateVisibility() {
        var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(QilletniSimulatorService.TOOL_WINDOW_ID);
        decorations.setVisible(toolWindow != null && toolWindow.isVisible());
    }

    private void showEmpty(String key) {
        decorations.show(QilletniSimulatorService.getInstance(project).source(), List.of(), null);
        emptyLabel.setText(QilletniBundle.message(key));
        cards.show((JPanel) getComponent(0), EMPTY_CARD);
    }

    /** If the songs the simulator draws from are unchanged, so it doesn't need rebuilding. */
    private static boolean sameSongs(QilletniWeightStats.SimulationInput a, QilletniWeightStats.SimulationInput b) {
        return a.entrySongs().keySet().equals(b.entrySongs().keySet()) && a.seenSongs().equals(b.seenSongs());
    }

    private void extend(Simulation simulation) {
        var result = simulation.simulator.simulate(simulation.state, BATCH);
        simulation.picks.addAll(result.picks());
        simulation.blocked = result.blocked();
    }

    /** Works out the long-run mix in the background whenever what the simulator draws from changes. */
    private void updateLongRun(Simulation simulation) {
        var key = List.of(simulation.input.breakdown(), simulation.input.entrySongs().keySet(), simulation.input.seenSongs().keySet());
        if (Objects.equals(simulation.longRunFor, key)) return;
        simulation.longRunFor = key;
        simulation.longRun = null;

        var input = simulation.input;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            var shares = new QilletniWeightSimulator(input, new Random()).longRun(LONG_RUN_PICKS);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!Objects.equals(simulation.longRunFor, key)) return;
                simulation.longRun = shares;
                if (shown() == simulation) render(simulation);
            }, project.getDisposed());
        });
    }

    private void render(Simulation simulation) {
        var simulator = simulation.simulator;
        var roots = simulator.roots();
        var focusable = roots.stream().filter(entry -> !entry.multiplier()).toList();
        if (focusable.isEmpty()) return;
        var focusEntry = focusable.get(Math.floorMod(simulation.focus, focusable.size()));
        simulation.focus = focusable.indexOf(focusEntry);
        var focusPosition = roots.indexOf(focusEntry);
        var focusSongs = QilletniWeightSimulator.focusSongs(focusEntry);
        var songIndex = new HashMap<String, Integer>();
        for (int i = 0; i < focusSongs.size(); i++) songIndex.putIfAbsent(focusSongs.get(i).song().id(), i);

        var allBands = new ArrayList<StripComponent.Band>();
        var focusBands = new ArrayList<StripComponent.Band>();
        for (int i = 0; i < simulation.picks.size(); i++) {
            var pick = simulation.picks.get(i);
            var tooltip = tooltip(simulator, i, pick);
            var rerolled = pick.rerollCount() > 0;
            if (pick.miss()) {
                allBands.add(new StripComponent.Band(SimulatorPalette.MISS, true, rerolled, tooltip, null));
                focusBands.add(new StripComponent.Band(SimulatorPalette.OTHER, false, rerolled, tooltip, null));
                continue;
            }

            var position = simulator.rootPosition(pick.top());
            var unknown = pick.song() != null && pick.song().unknown();
            allBands.add(new StripComponent.Band(SimulatorPalette.entry(position), false, rerolled, tooltip, pick.indices()));
            focusBands.add(position == focusPosition
                    ? new StripComponent.Band(focusColor(focusPosition, focusSongs, songIndex, pick.song()), unknown, rerolled, tooltip, pick.indices())
                    : new StripComponent.Band(SimulatorPalette.OTHER, false, rerolled, tooltip, pick.indices()));
        }

        var realBands = new ArrayList<StripComponent.Band>();
        var history = simulation.input.history();
        for (int i = 0; i < history.size(); i++) {
            var real = history.get(i);
            if (real.miss()) {
                var color = viewAll.isSelected() ? SimulatorPalette.MISS : SimulatorPalette.OTHER;
                realBands.add(new StripComponent.Band(color, viewAll.isSelected(), false,
                        html(List.of("#" + (i + 1) + "  " + QilletniBundle.message("debugger.simulator.tooltip.miss"), "→ " + real.song())), null));
                continue;
            }
            var indices = QilletniWeightStats.indices(real.key());
            var position = simulator.rootPosition(indices.getFirst());
            var song = new QilletniWeightSimulator.Song(real.trackId(), real.song(), false);
            Color color;
            if (viewAll.isSelected()) color = SimulatorPalette.entry(position);
            else color = position == focusPosition ? focusColor(focusPosition, focusSongs, songIndex, song) : SimulatorPalette.OTHER;
            realBands.add(new StripComponent.Band(color, false, false, html(List.of("#" + (i + 1) + "  " + pathText(simulator, indices), "→ " + real.song())), indices));
        }

        allStrip.setBands(allBands, 0);
        focusStrip.setBands(focusBands, 0);
        realStrip.setBands(realBands, realBands.size() > StripComponent.DENSE_AFTER ? 0 : Math.max(0, Math.min(allBands.size(), StripComponent.DENSE_AFTER) - realBands.size()));

        countLabel.setText(QilletniBundle.message("debugger.simulator.count", QilletniWeightStats.count(simulation.picks.size()), QilletniWeightStats.count(history.size())));
        blockedLabel.setText(simulation.blocked ? QilletniBundle.message("debugger.simulator.blocked") : "");
        renderFocusHeader(focusEntry);
        renderLegend(simulation, roots, focusable, focusEntry);
        renderMix(simulation, roots);

        var source = QilletniSimulatorService.getInstance(project).source();
        List<Integer> latest = null;
        if (source != null && source.isAlive() && !history.isEmpty() && !history.getLast().miss()) {
            latest = QilletniWeightStats.indices(history.getLast().key());
        }
        updateVisibility();
        decorations.show(source, roots, latest);
        revalidate();
        repaint();
    }

    private static Color focusColor(int position, List<QilletniWeightSimulator.FocusSong> songs, Map<String, Integer> index,
                                    @Nullable QilletniWeightSimulator.Song song) {
        var i = song == null ? null : index.get(song.id());
        if (i == null) return SimulatorPalette.entry(position);
        return SimulatorPalette.song(position, i, songs.size(), songs.get(i).nested());
    }

    private void renderFocusHeader(QilletniWeightSimulator.Entry focus) {
        focusTitle.setText(QilletniBundle.message("debugger.simulator.focus", label(focus)));

        var notes = new ArrayList<String>();
        var unloaded = new ArrayList<QilletniWeightSimulator.Entry>();
        collectNotes(focus, notes, unloaded);
        focusNote.setText(String.join("  ·  ", notes));

        var source = QilletniSimulatorService.getInstance(project).source();
        var alive = source != null && source.isAlive();
        loadSongs.setVisible(!unloaded.isEmpty());
        loadSongs.setEnabled(alive);
        loadSongs.setToolTipText(alive ? null : QilletniBundle.message("debugger.simulator.loadSongs.ended"));
    }

    private static void collectNotes(QilletniWeightSimulator.Entry entry, List<String> notes, List<QilletniWeightSimulator.Entry> unloaded) {
        if (!entry.leaf()) {
            entry.children().forEach(child -> collectNotes(child, notes, unloaded));
            return;
        }

        var name = label(entry);
        switch (entry.kind()) {
            case "function" -> notes.add(entry.songsKnown()
                    ? QilletniBundle.message("debugger.simulator.note.functionSeen", entry.source(), entry.songs().size())
                    : QilletniBundle.message("debugger.simulator.note.functionUnknown", entry.source()));
            case "list", "album", "collection" -> {
                if (!entry.songsKnown()) {
                    notes.add(QilletniBundle.message("debugger.simulator.note.notLoaded", name));
                    unloaded.add(entry);
                } else if (entry.approximate()) {
                    notes.add(QilletniBundle.message("debugger.simulator.note.approximate", name));
                }
                if (entry.sequential()) notes.add(QilletniBundle.message("debugger.simulator.note.sequential", name));
            }
            default -> {
            }
        }
    }

    /** Focuses the top-level entry with {@code index}, as its legend button does. */
    private void focus(int index) {
        var simulation = shown();
        if (simulation == null) return;
        var focusable = simulation.simulator.roots().stream().filter(entry -> !entry.multiplier()).toList();
        for (int i = 0; i < focusable.size(); i++) {
            if (focusable.get(i).index() == index) {
                simulation.focus = i;
                render(simulation);
                return;
            }
        }
    }

    private void loadFocusSongs() {
        var simulation = shown();
        var source = QilletniSimulatorService.getInstance(project).source();
        if (simulation == null || source == null || !source.isAlive()) return;

        var focusable = simulation.simulator.roots().stream().filter(entry -> !entry.multiplier()).toList();
        var notes = new ArrayList<String>();
        var unloaded = new ArrayList<QilletniWeightSimulator.Entry>();
        collectNotes(focusable.get(simulation.focus), notes, unloaded);
        for (var entry : unloaded) {
            if (SongLoadConfirmation.confirm(this, entry.songCount(), label(entry))) {
                source.loadSongs(simulation.input.id(), entry.key());
            }
        }
    }

    private void renderLegend(Simulation simulation, List<QilletniWeightSimulator.Entry> roots,
                              List<QilletniWeightSimulator.Entry> focusable, QilletniWeightSimulator.Entry focus) {
        legend.removeAll();
        var group = new ButtonGroup();
        for (var entry : focusable) {
            var button = new JToggleButton(label(entry), new ColorIcon(JBUI.scale(12), SimulatorPalette.entry(roots.indexOf(entry))), entry == focus);
            button.setToolTipText(QilletniBundle.message("debugger.simulator.legend.focus", label(entry)));
            button.addActionListener(e -> {
                simulation.focus = focusable.indexOf(entry);
                render(simulation);
                var indices = List.of(entry.index());
                decorations.setBox(SimulatorEditorDecorations.Box.PINNED, indices);
                decorations.navigate(indices);
            });
            group.add(button);
            legend.add(button);
        }

        var breakdown = simulation.input.breakdown();
        if (breakdown != null && breakdown.miss > 0) {
            legend.add(new JBLabel(QilletniBundle.message("debugger.simulator.legend.miss"), new ColorIcon(JBUI.scale(12), SimulatorPalette.MISS), SwingConstants.LEFT));
        }
        legend.add(new JBLabel(QilletniBundle.message("debugger.simulator.legend.other"), new ColorIcon(JBUI.scale(12), SimulatorPalette.OTHER), SwingConstants.LEFT));
    }

    private void renderMix(Simulation simulation, List<QilletniWeightSimulator.Entry> roots) {
        var breakdown = simulation.input.breakdown();
        var miss = breakdown != null ? breakdown.miss : 0;

        var chance = new double[roots.size() + 1];
        for (int i = 0; i < roots.size(); i++) chance[i] = roots.get(i).multiplier() ? 0 : roots.get(i).combined();
        chance[roots.size()] = miss;

        var simulated = new double[roots.size() + 1];
        for (var pick : simulation.picks) simulated[pick.miss() ? roots.size() : simulation.simulator.rootPosition(pick.top())]++;
        for (int i = 0; i < simulated.length; i++) simulated[i] = simulation.picks.isEmpty() ? 0 : simulated[i] * 100 / simulation.picks.size();

        var bars = new ArrayList<MixBarsComponent.Bar>();
        var longRunTooltip = QilletniBundle.message("debugger.simulator.mix.longRun.tooltip", QilletniWeightStats.count(LONG_RUN_PICKS));
        bars.add(bar(QilletniBundle.message("debugger.simulator.mix.chance"), chance, roots, QilletniBundle.message("debugger.simulator.mix.chance.tooltip")));
        bars.add(simulation.longRun != null
                ? bar(QilletniBundle.message("debugger.simulator.mix.longRun"), simulation.longRun, roots, longRunTooltip)
                : new MixBarsComponent.Bar(QilletniBundle.message("debugger.simulator.mix.longRun"), List.of(),
                        QilletniBundle.message("debugger.simulator.mix.longRun.calculating")));
        bars.add(bar(QilletniBundle.message("debugger.simulator.mix.simulated"), simulated, roots,
                QilletniBundle.message("debugger.simulator.mix.simulated.tooltip", QilletniWeightStats.count(simulation.picks.size()))));
        mix.setBars(bars);
    }

    private static MixBarsComponent.Bar bar(String label, double[] shares, List<QilletniWeightSimulator.Entry> roots, String tooltip) {
        var segments = new ArrayList<MixBarsComponent.Segment>();
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).multiplier() || shares[i] <= 0) continue;
            segments.add(new MixBarsComponent.Segment(SimulatorPalette.entry(i), false, shares[i],
                    QilletniBundle.message("debugger.simulator.mix.tooltip", label, label(roots.get(i)), QilletniWeightStats.percent(shares[i]))));
        }
        if (shares[roots.size()] > 0) {
            segments.add(new MixBarsComponent.Segment(SimulatorPalette.MISS, true, shares[roots.size()],
                    QilletniBundle.message("debugger.simulator.mix.tooltip", label, QilletniBundle.message("debugger.simulator.legend.miss"),
                            QilletniWeightStats.percent(shares[roots.size()]))));
        }
        return new MixBarsComponent.Bar(label, segments, tooltip);
    }

    private static String tooltip(QilletniWeightSimulator simulator, int index, QilletniWeightSimulator.SimPick pick) {
        var lines = new ArrayList<String>();
        if (pick.miss()) {
            lines.add("#" + (index + 1) + "  " + QilletniBundle.message("debugger.simulator.tooltip.miss"));
        } else {
            lines.add("#" + (index + 1) + "  " + pathText(simulator, pick.indices()));
            var song = Objects.requireNonNull(pick.song());
            lines.add("→ " + (song.unknown() ? QilletniBundle.message("debugger.simulator.tooltip.unknown", song.text()) : song.text()));
        }

        if (pick.rerollCount() > 0) {
            var shown = pick.rerolls().stream().limit(SHOWN_REROLLS)
                    .map(reroll -> "%s \"%s\" (%s)".formatted(pathText(simulator, reroll.indices()), reroll.song().text(),
                            QilletniBundle.message(reroll.reason() == QilletniWeightSimulator.Reason.SAME_ENTRY
                                    ? "debugger.simulator.tooltip.sameEntry" : "debugger.simulator.tooltip.sameSong")))
                    .collect(Collectors.joining("; "));
            lines.add(QilletniBundle.message("debugger.simulator.tooltip.rerolled", pick.rerollCount(), shown));
        }
        return html(lines);
    }

    private static String pathText(QilletniWeightSimulator simulator, List<Integer> indices) {
        var names = new ArrayList<String>();
        var entries = simulator.roots();
        for (var index : indices) {
            var entry = entries.stream().filter(e -> e.index() == index).findFirst().orElse(null);
            if (entry == null) break;
            names.add(entry.name() + " " + entry.source());
            entries = entry.children();
        }
        return String.join(" → ", names);
    }

    /** e.g. {@code |~ 35% "Blisstonia" by "Icreatedamonster" (6 songs)} */
    private static String label(QilletniWeightSimulator.Entry entry) {
        var amount = entry.name().replaceFirst("^\\[\\d+] \\|[!~]?", "");
        var separator = entry.separator() != null ? entry.separator() + " " : "";
        return separator + amount + " " + QilletniWeightStats.shortSource(entry.kind(), entry.source(), entry.songCount());
    }

    private static String html(List<String> lines) {
        return "<html>" + lines.stream().map(StringUtil::escapeXmlEntities).collect(Collectors.joining("<br>")) + "</html>";
    }
}
