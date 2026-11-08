package dev.qilletni.intellij.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Application-level debugger settings, shown on the Qilletni settings page.
 */
@State(name = "QilletniDebuggerSettings", storages = @Storage("qilletni_debugger.xml"))
public final class QilletniDebuggerSettings implements PersistentStateComponent<QilletniDebuggerSettings.StateBean> {
    public static final int DEFAULT_WEIGHT_SONG_LIMIT = 25;

    public static final class StateBean {
        /**
         * Lists and albums in the Weights tab with at most this many songs show all of them. Larger ones, and every
         * collection, show them on request after a confirmation.
         */
        public int weightSongLimit = DEFAULT_WEIGHT_SONG_LIMIT;
    }

    private StateBean state = new StateBean();

    public static QilletniDebuggerSettings getInstance() {
        return ApplicationManager.getApplication().getService(QilletniDebuggerSettings.class);
    }

    public int getWeightSongLimit() {
        return state.weightSongLimit;
    }

    public void setWeightSongLimit(int limit) {
        state.weightSongLimit = Math.max(0, limit);
    }

    @Override
    public @NotNull StateBean getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateBean state) {
        this.state = state;
    }
}
