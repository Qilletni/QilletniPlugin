package dev.qilletni.intellij.debugger.dap;

import java.util.List;

/** The body of a {@code qilletni/weightEntrySongs} response. */
public class WeightEntrySongsResponse {
    public List<Song> songs;
    /** If every song is equally likely when the entry is picked. */
    public boolean evenChance;

    public static class Song {
        public String song;
        public String trackId;
    }
}
