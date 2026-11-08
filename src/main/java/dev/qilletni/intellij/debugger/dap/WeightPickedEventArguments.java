package dev.qilletni.intellij.debugger.dap;

import java.util.List;

/**
 * The body of a {@code qilletni/weightPicked} event. Chances, rolls and ranges are in percent. See
 * {@code docs/debugger-protocol.md} for each field.
 */
public class WeightPickedEventArguments {
    public int weightsId;
    public String label;
    /** {@code pick}, or {@code miss} when no entry was rolled and the collection's own queue was used. */
    public String kind;
    public List<Level> path;
    public String song;
    public String trackId;
    public List<Rejected> rejected;
    public int rejectedOmitted;
    /** Only sent when it changed since the last event for these weights. */
    public Breakdown breakdown;

    public static class Level {
        public int index;
        public String name;
        public double roll;
        public double from;
        public double to;
        public double chance;
    }

    public static class Rejected {
        public List<Integer> indices;
        public List<String> path;
        public String song;
        public String reason;
    }

    public static class Breakdown {
        public List<Node> roots;
        public double miss;
        public int omitted;
        public double omittedChance;
    }

    public static class Node {
        public String name;
        public int index;
        public String kind;
        public double chance;
        public double combined;
        public String source;
        public int listSize;
        /** The number of songs a list, collection or album entry picks from. */
        public int songCount;
        /** The entry's amount as written, a percent or a multiplier. */
        public double amount;
        /** If the entry is a collection or album played in order. */
        public boolean sequential;
        /** Where the entry is written: a DAP source path and 1-based lines and columns, the end column exclusive. */
        public String sourcePath;
        public int line;
        public int column;
        public int endLine;
        public int endColumn;
        public List<Node> children;
    }
}
