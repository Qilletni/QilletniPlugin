package dev.qilletni.intellij.project;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniProjectLibraries;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the project's Qilletni dependencies when it opens, so library roots, imports and native bindings are
 * available without any manual step.
 */
public final class QilletniLibrariesStartupActivity implements ProjectActivity, DumbAware {
    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        QilletniProjectLibraries.removeLegacyLibraries(project);
        QilletniProjectDependencies.getInstance(project).refresh();
        return Unit.INSTANCE;
    }
}
