package dev.qilletni.intellij.library;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application-level settings storing the root folder path containing .qll archives.
 * Expected structure: <root>/<scope>/<name>/<version>.qll
 */
@State(name = "QilletniLibrariesSettings", storages = @Storage("QilletniLibrariesSettings.xml"))
public final class QilletniLibrariesSettings implements PersistentStateComponent<QilletniLibrariesSettings.State> {

    public static final class State {
        public String librariesRootPath = ""; // absolute path; empty = disabled
    }

    private State state = new State();

    public static QilletniLibrariesSettings getInstance() {
        return ApplicationManager.getApplication().getService(QilletniLibrariesSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    /**
     * Returns the configured libraries root path (absolute path), or empty if not set.
     */
    public String getLibrariesRootPath() {
        return state != null ? state.librariesRootPath : "";
    }

    /**
     * Updates the libraries root path.
     */
    public void setLibrariesRootPath(String path) {
        if (state == null) state = new State();
        state.librariesRootPath = path != null ? path : "";
    }
}
