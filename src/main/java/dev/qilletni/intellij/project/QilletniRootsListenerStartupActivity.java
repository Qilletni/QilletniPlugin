package dev.qilletni.intellij.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import dev.qilletni.intellij.resolve.QilletniAliasResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Startup activity that invalidates Qilletni alias/import caches when project roots change or when
 * 'qilletni-src' directories or .ql files are created, deleted, moved, or renamed within the project.
 */
public final class QilletniRootsListenerStartupActivity implements StartupActivity.DumbAware {
    private static final String QILLETNI_DIR = "qilletni-src";

    @Override
    public void runActivity(@NotNull Project project) {
        // Listen for project roots changes (modules/content roots added or removed)
        project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
            @Override
            public void rootsChanged(@NotNull ModuleRootEvent event) {
                QilletniAliasResolver.onRootsChanged();
            }
        });

        // Listen for structural VFS changes where qilletni-src or .ql files appear/disappear in this project.
        // Content changes (saves) are skipped: PSI modification already invalidates the cached values.
        project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
                if (project.isDisposed()) return;
                var fileIndex = ProjectFileIndex.getInstance(project);
                for (var e : events) {
                    if (e instanceof VFileContentChangeEvent) continue;
                    var f = e.getFile();
                    if (f == null || !(QILLETNI_DIR.equals(f.getName()) || f.getName().endsWith(".ql"))) continue;
                    if (f.isValid() && !fileIndex.isInContent(f)) continue;
                    QilletniAliasResolver.onRootsChanged();
                    break;
                }
            }
        });
    }
}
