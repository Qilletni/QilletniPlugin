package dev.qilletni.intellij.debugger.simulator;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Stacked 100% bars, one per row, e.g. Chance, Long-run and Simulated, with a tooltip per segment.
 */
final class MixBarsComponent extends JComponent {
    record Segment(@NotNull Color color, boolean hatched, double share, @NotNull String tooltip) {}

    /**
     * @param tooltip Explains the bar, shown over its label and its empty parts, or {@code null}
     */
    record Bar(@NotNull String label, @NotNull List<Segment> segments, @Nullable String tooltip) {}

    private List<Bar> bars = List.of();

    MixBarsComponent() {
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    void setBars(@NotNull List<Bar> bars) {
        this.bars = bars;
        revalidate();
        repaint();
    }

    private static int rowHeight() {
        return JBUI.scale(18);
    }

    private static int rowGap() {
        return JBUI.scale(6);
    }

    private static int labelWidth() {
        return JBUI.scale(84);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(JBUI.scale(300), bars.size() * (rowHeight() + rowGap()));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().getSize2D() - 1));
            var metrics = g.getFontMetrics();
            for (int row = 0; row < bars.size(); row++) {
                var bar = bars.get(row);
                var y = row * (rowHeight() + rowGap());
                g.setColor(UIUtil.getContextHelpForeground());
                g.drawString(bar.label(), 0, y + (rowHeight() - metrics.getHeight()) / 2 + metrics.getAscent());

                for (var segment : segments(bar, y)) {
                    g.setColor(segment.segment().color());
                    g.fillRect(segment.bounds().x, segment.bounds().y, segment.bounds().width, segment.bounds().height);
                    if (segment.segment().hatched()) {
                        var r = segment.bounds();
                        var clip = g.getClip();
                        g.clipRect(r.x, r.y, r.width, r.height);
                        g.setColor(SimulatorPalette.HATCH);
                        for (int x = r.x - r.height; x < r.x + r.width; x += JBUI.scale(4)) g.drawLine(x, r.y + r.height, x + r.height, r.y);
                        g.setClip(clip);
                    }
                }
                g.setColor(JBColor.border());
                g.drawRect(labelWidth(), y, getWidth() - labelWidth() - 1, rowHeight() - 1);
            }
        } finally {
            g.dispose();
        }
    }

    private record Placed(Segment segment, Rectangle bounds) {}

    private List<Placed> segments(Bar bar, int y) {
        var width = getWidth() - labelWidth();
        var placed = new java.util.ArrayList<Placed>();
        double start = 0;
        for (var segment : bar.segments()) {
            var x0 = labelWidth() + (int) Math.round(start / 100 * width);
            start += Math.max(0, segment.share());
            var x1 = labelWidth() + (int) Math.round(Math.min(100, start) / 100 * width);
            if (x1 > x0) placed.add(new Placed(segment, new Rectangle(x0, y, x1 - x0, rowHeight())));
        }
        return placed;
    }

    @Override
    public @Nullable String getToolTipText(@NotNull MouseEvent event) {
        for (int row = 0; row < bars.size(); row++) {
            var y = row * (rowHeight() + rowGap());
            if (event.getY() < y || event.getY() >= y + rowHeight()) continue;
            for (var segment : segments(bars.get(row), y)) {
                if (segment.bounds().contains(event.getPoint())) return segment.segment().tooltip();
            }
            return bars.get(row).tooltip();
        }
        return null;
    }
}
