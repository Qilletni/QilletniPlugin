package dev.qilletni.intellij.debugger;

/**
 * "Song played": off by default, maps to the adapter's {@value #FILTER} filter, which stops before each track plays.
 */
public final class QilletniSongPlayedBreakpointType extends QilletniFilterBreakpointType {
    public static final String ID = "qilletni-song-played";
    public static final String FILTER = "songPlayed";
    public static final String STOP_REASON = "song played";

    public QilletniSongPlayedBreakpointType() {
        super(ID, "debugger.breakpoint.songPlayed.title", "debugger.breakpoint.songPlayed.display", FILTER, STOP_REASON, false);
    }
}
