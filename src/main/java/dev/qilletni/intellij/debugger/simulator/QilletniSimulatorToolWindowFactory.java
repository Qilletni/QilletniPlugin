package dev.qilletni.intellij.debugger.simulator;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import dev.qilletni.intellij.toolwindow.QilletniToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * The Weights Simulator tool window, available in the same projects as the Qilletni tool window.
 */
public final class QilletniSimulatorToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var panel = new QilletniSimulatorPanel(project);
        var content = ContentFactory.getInstance().createContent(panel, null, false);
        Disposer.register(content, panel);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return new QilletniToolWindowFactory().shouldBeAvailable(project);
    }
}
