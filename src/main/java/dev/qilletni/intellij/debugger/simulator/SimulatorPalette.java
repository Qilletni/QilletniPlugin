package dev.qilletni.intellij.debugger.simulator;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * Calm colors for the Weights Simulator: one muted color per top-level entry, shades of it for the focused entry's
 * songs, and a neutral grey for everything else.
 */
final class SimulatorPalette {
    private static final JBColor[] ENTRIES = {
            new JBColor(0x5B8DBE, 0x6C9BCB),
            new JBColor(0xC9845A, 0xC98D63),
            new JBColor(0x6FA67A, 0x78AE83),
            new JBColor(0xB5A04E, 0xBCA757),
            new JBColor(0x9A7CB8, 0xA487C1),
            new JBColor(0x4FA3A0, 0x5BAEAB),
            new JBColor(0xC47A8E, 0xCB8698),
            new JBColor(0x8C8F5A, 0x969966),
    };

    static final JBColor OTHER = new JBColor(0xCDD1D7, 0x484D55);
    static final JBColor MISS = new JBColor(0xB4B9C0, 0x5C626B);
    static final JBColor HATCH = new JBColor(new Color(0, 0, 0, 70), new Color(0, 0, 0, 110));
    static final JBColor EMPTY = new JBColor(0xE6E9ED, 0x2E3238);

    private SimulatorPalette() {}

    /** @param position The entry's position among the top-level entries */
    static @NotNull Color entry(int position) {
        return ENTRIES[Math.floorMod(position, ENTRIES.length)];
    }

    /**
     * A shade of an entry's color for one of its songs: lighter further down the list, and duller for songs under
     * nested weights.
     *
     * @param index The song's position among the entry's songs
     */
    static @NotNull Color song(int position, int index, int count, boolean nested) {
        var base = entry(position);
        var hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        var step = count <= 1 ? 0.5f : (float) index / (count - 1);
        var saturation = Math.min(1f, hsb[1] * (nested ? 0.5f : 1.15f));
        var brightness = 0.48f + 0.47f * step;
        return Color.getHSBColor(hsb[0], saturation, brightness);
    }
}