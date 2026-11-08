package dev.qilletni.intellij.debugger.weights;

import com.intellij.openapi.ui.Messages;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.debugger.QilletniDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;

/**
 * Asks before showing every song of an entry with more songs than the debugger settings show automatically.
 */
public final class SongLoadConfirmation {

    private SongLoadConfirmation() {}

    /**
     * @param name The entry, e.g. {@code "Big" collection (3,000 songs)}
     * @return If the songs should be fetched
     */
    public static boolean confirm(@NotNull Component parent, int songCount, @NotNull String name) {
        if (songCount <= QilletniDebuggerSettings.getInstance().getWeightSongLimit()) return true;
        return Messages.showYesNoDialog(parent,
                QilletniBundle.message("debugger.weights.showSongs.confirm", QilletniWeightStats.count(songCount), name),
                QilletniBundle.message("debugger.weights.showSongs"), Messages.getQuestionIcon()) == Messages.YES;
    }
}
