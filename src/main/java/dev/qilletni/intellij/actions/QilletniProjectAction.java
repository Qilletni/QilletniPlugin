package dev.qilletni.intellij.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniProjectModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An action on one Qilletni project: the one selected in the Qilletni tool window, else the one containing the
 * current file, else the only one in the IDE project. Hidden when there is none.
 */
public abstract class QilletniProjectAction extends DumbAwareAction {
    /** Provided by the Qilletni tool window for its selection. */
    public static final DataKey<QilletniProjectModel> MODEL = DataKey.create("qilletni.project.model");

    protected abstract void perform(@NotNull Project project, @NotNull QilletniProjectModel model);

    /** Whether the action applies to this project; disabled (but visible) otherwise. */
    protected boolean isApplicable(@NotNull QilletniProjectModel model) {
        return true;
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        var model = findModel(e);
        if (project != null && model != null) perform(project, model);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        var model = findModel(e);
        e.getPresentation().setVisible(model != null);
        e.getPresentation().setEnabled(model != null && isApplicable(model));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    public static @Nullable QilletniProjectModel findModel(@NotNull AnActionEvent e) {
        var selected = e.getData(MODEL);
        if (selected != null) return selected;
        var project = e.getProject();
        if (project == null) return null;
        var dependencies = QilletniProjectDependencies.getInstance(project);
        var fromFile = dependencies.findProject(e.getData(CommonDataKeys.VIRTUAL_FILE));
        if (fromFile != null) return fromFile;
        var projects = dependencies.getState().projects();
        return projects.size() == 1 ? projects.getFirst() : null;
    }
}
