package dev.qilletni.intellij.project;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Opens the starter file of a newly created Qilletni project. */
public final class QilletniPostInitOpener {
    private QilletniPostInitOpener() {}

    public static void openPrimaryFile(@NotNull Project project, @Nullable VirtualFile baseDir, QilletniNewProjectWizardStep.ProjectType type) {
        if (baseDir == null || project.isDisposed()) return;
        var editors = FileEditorManager.getInstance(project);
        // Libraries are tried out through their example; applications start in qilletni-src
        var dirs = type == QilletniNewProjectWizardStep.ProjectType.LIBRARY ? new String[]{"examples", "qilletni-src"} : new String[]{"qilletni-src"};
        for (var dirName : dirs) {
            var dir = baseDir.findChild(dirName);
            if (dir == null) continue;
            for (var child : dir.getChildren()) {
                if (!child.isDirectory() && child.getName().endsWith(".ql")) {
                    editors.openFile(child, true);
                    return;
                }
            }
        }
        var readme = baseDir.findChild("README.md");
        if (readme != null) editors.openFile(readme, true);
    }
}
