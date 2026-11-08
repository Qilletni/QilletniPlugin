package dev.qilletni.intellij.debugger.simulator;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import dev.qilletni.intellij.debugger.weights.QilletniWeightStats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connects the Weights Simulator tool window to the latest Qilletni debug session. The session's data stays after it
 * ends, so its weights can still be simulated.
 */
@Service(Service.Level.PROJECT)
public final class QilletniSimulatorService {
    public static final String TOOL_WINDOW_ID = "Weights Simulator";

    /** Where the simulator gets its data: a debug session. */
    public interface Source {
        @NotNull QilletniWeightStats stats();

        /** If the session is still running, so songs can be fetched. */
        boolean isAlive();

        /** Fetches every song of an entry; the source calls {@link #changed()} once they arrive. */
        void loadSongs(int weightsId, @NotNull String key);

        /** The file of a DAP source path, or {@code null} if it isn't known. */
        @Nullable VirtualFile file(@NotNull String path);
    }

    public interface Listener {
        /** The source changed, or has new picks or songs. Called on any thread. */
        void changed();

        /** The user asked to see these weights. Called on the EDT. */
        default void show(int weightsId) {}
    }

    private final Project project;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile @Nullable Source source;

    public QilletniSimulatorService(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull QilletniSimulatorService getInstance(@NotNull Project project) {
        return project.getService(QilletniSimulatorService.class);
    }

    public @Nullable Source source() {
        return source;
    }

    /** Makes {@code source} the one shown, replacing any earlier session. */
    public void setSource(@NotNull Source source) {
        this.source = source;
        changed();
    }

    public void changed() {
        listeners.forEach(Listener::changed);
    }

    public void addListener(@NotNull Listener listener, @NotNull Disposable parent) {
        listeners.add(listener);
        Disposer.register(parent, () -> listeners.remove(listener));
    }

    /** Opens the tool window on {@code weightsId}. */
    public void show(int weightsId) {
        ApplicationManager.getApplication().invokeLater(() -> {
            var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
            if (toolWindow == null) return;
            toolWindow.activate(() -> listeners.forEach(listener -> listener.show(weightsId)));
        }, project.getDisposed());
    }
}
