package dev.qilletni.intellij.toolchain;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Application-level Qilletni settings. Every value is optional; blank values are auto-detected by
 * {@link QilletniInstallation}.
 */
@State(name = "QilletniToolchainSettings", storages = @Storage("qilletni_toolchain.xml"))
public final class QilletniToolchainSettings implements PersistentStateComponent<QilletniToolchainSettings.StateBean> {
    public static final class StateBean {
        /** Path to QilletniToolchain.jar; blank = detect from the Qilletni home. */
        public String toolchainPath;
        /** Qilletni home directory; blank = ~/.qilletni. */
        public String qilletniHome;
        /** Name of the JDK used to run the toolchain; blank = Project SDK. */
        public String jdkName;
    }

    private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();
    private StateBean state = new StateBean();

    public static QilletniToolchainSettings getInstance() {
        return ApplicationManager.getApplication().getService(QilletniToolchainSettings.class);
    }

    public ModificationTracker getModificationTracker() { return modificationTracker; }

    public @Nullable String getToolchainPath() { return state.toolchainPath; }

    public @Nullable String getQilletniHome() { return state.qilletniHome; }

    public @Nullable String getJdkName() { return state.jdkName; }

    public void setToolchainPath(@Nullable String path) {
        if (!Objects.equals(state.toolchainPath, path)) {
            state.toolchainPath = path;
            modificationTracker.incModificationCount();
        }
    }

    public void setQilletniHome(@Nullable String home) {
        if (!Objects.equals(state.qilletniHome, home)) {
            state.qilletniHome = home;
            modificationTracker.incModificationCount();
        }
    }

    public void setJdkName(@Nullable String jdkName) {
        if (!Objects.equals(state.jdkName, jdkName)) {
            state.jdkName = jdkName;
            modificationTracker.incModificationCount();
        }
    }

    @Override
    public @Nullable StateBean getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateBean state) {
        this.state = state;
        modificationTracker.incModificationCount();
    }
}
