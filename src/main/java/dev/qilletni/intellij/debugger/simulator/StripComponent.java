package dev.qilletni.intellij.debugger.simulator;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * One band per pick, top to bottom. Past {@link #DENSE_AFTER} picks the bands get thin and wrap into columns of
 * {@link #COLUMN}, read top to bottom, then left to right.
 */
final class StripComponent extends JComponent {
    static final int DENSE_AFTER = 60;
    static final int COLUMN = 50;

    /**
     * @param hatched  The song isn't known, so the band is striped
     * @param rerolled The pick needed rerolls, so the band has a notch at its left edge
     * @param indices  The picked entry's index at each level, or {@code null} for a miss
     */
    record Band(@NotNull Color color, boolean hatched, boolean rerolled, @NotNull String tooltip, @Nullable List<Integer> indices) {}

    /** Hovering and clicking bands, with the band's entry indices, or {@code null} off any band or for a miss. */
    interface Listener {
        void hovered(@Nullable List<Integer> indices);

        void clicked(@Nullable List<Integer> indices);
    }

    private List<Band> bands = List.of();
    private int placeholders;

    StripComponent(@NotNull Listener listener) {
        ToolTipManager.sharedInstance().registerComponent(this);
        var mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                var band = bandAt(event);
                listener.hovered(band == null ? null : band.indices());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                listener.hovered(null);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                var band = bandAt(event);
                listener.clicked(band == null ? null : band.indices());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private @Nullable Band bandAt(MouseEvent event) {
        for (int i = 0; i < bands.size(); i++) {
            var r = bounds(i);
            if (r != null && r.contains(event.getPoint())) return bands.get(i);
        }
        return null;
    }

    /**
     * @param placeholders Empty rows to draw after the bands, e.g. while waiting for real picks
     */
    void setBands(@NotNull List<Band> bands, int placeholders) {
        this.bands = bands;
        this.placeholders = placeholders;
        revalidate();
        repaint();
    }

    private boolean dense() {
        return bands.size() > DENSE_AFTER;
    }

    @Override
    public Dimension getPreferredSize() {
        if (dense()) {
            var columns = (bands.size() + COLUMN - 1) / COLUMN;
            return new Dimension(columns * (columnWidth() + gap()), COLUMN * JBUI.scale(4));
        }
        return new Dimension(JBUI.scale(140), Math.max(1, bands.size() + placeholders) * JBUI.scale(16));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(JBUI.scale(60), getPreferredSize().height);
    }

    private static int columnWidth() {
        return JBUI.scale(22);
    }

    private static int gap() {
        return JBUI.scale(6);
    }

    private @Nullable Rectangle bounds(int index) {
        if (dense()) {
            int column = index / COLUMN, row = index % COLUMN, height = JBUI.scale(4);
            return new Rectangle(column * (columnWidth() + gap()), row * height, columnWidth(), height);
        }
        var height = JBUI.scale(16);
        return new Rectangle(0, index * height, getWidth(), height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            for (int i = 0; i < bands.size(); i++) {
                var band = bands.get(i);
                var r = bounds(i);
                if (r == null) continue;
                g.setColor(band.color());
                g.fillRect(r.x, r.y, r.width, r.height);
                if (band.hatched()) hatch(g, r);
                if (band.rerolled()) hatch(g, new Rectangle(r.x, r.y, Math.min(r.width, JBUI.scale(dense() ? 3 : 5)), r.height));
                if (!dense()) {
                    g.setColor(UIUtil.getPanelBackground());
                    g.drawLine(r.x, r.y + r.height - 1, r.x + r.width, r.y + r.height - 1);
                }
            }

            if (!dense()) {
                var height = JBUI.scale(16);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0f));
                for (int i = 0; i < placeholders; i++) {
                    var y = (bands.size() + i) * height;
                    g.setColor(SimulatorPalette.EMPTY);
                    g.drawRect(0, y, getWidth() - 1, height - 2);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static void hatch(Graphics2D g, Rectangle r) {
        var clip = g.getClip();
        g.clipRect(r.x, r.y, r.width, r.height);
        g.setColor(SimulatorPalette.HATCH);
        var step = JBUI.scale(4);
        for (int x = r.x - r.height; x < r.x + r.width; x += step) {
            g.drawLine(x, r.y + r.height, x + r.height, r.y);
        }
        g.setClip(clip);
    }

    @Override
    public @Nullable String getToolTipText(@NotNull MouseEvent event) {
        var band = bandAt(event);
        return band == null ? null : band.tooltip();
    }
}
