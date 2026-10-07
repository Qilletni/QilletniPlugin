package dev.qilletni.intellij.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.model.QilletniManifest;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the Qilletni tool window. It is only available in projects containing a Qilletni project; it becomes
 * available as soon as one is created (see {@link QilletniProjectDependencies}).
 */
public final class QilletniToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String ID = "Qilletni";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var panel = new QilletniToolWindowPanel(project);
        var content = ContentFactory.getInstance().createContent(panel, null, false);
        Disposer.register(content, panel);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        var base = project.getBasePath();
        return base != null && Files.isRegularFile(Path.of(base, QilletniProjectDependencies.SRC_DIR, QilletniManifest.FILE_NAME))
                || !QilletniProjectDependencies.getInstance(project).getState().projects().isEmpty();
    }
}
