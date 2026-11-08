package dev.qilletni.intellij.library;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.EditorNotifications;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Alarm;
import com.intellij.util.messages.Topic;
import dev.qilletni.intellij.library.model.InstalledQllLibrary;
import dev.qilletni.intellij.library.model.QilletniLockfile;
import dev.qilletni.intellij.library.model.QilletniManifest;
import dev.qilletni.intellij.resolve.QilletniAliasResolver;
import dev.qilletni.intellij.toolchain.QilletniInstallation;
import dev.qilletni.intellij.toolwindow.QilletniToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Works out which Qilletni packages each Qilletni project in this IDE project depends on, the same way
 * {@code qilletni run} does: every package in {@code qilletni-src/qilletni.lock} is loaded from
 * {@code packages-local/<resolved>.qll}, falling back to {@code packages/<resolved>.qll}. Without a lockfile,
 * the declared dependencies are matched against the highest installed version satisfying their range.
 * <p>
 * The result drives library roots, {@code lib:} imports, auto-imports, the Qilletni tool window and editor banners.
 * It is recomputed in the background whenever a manifest, lockfile or installed package changes.
 */
@Service(Service.Level.PROJECT)
public final class QilletniProjectDependencies implements Disposable {
    private static final Logger LOG = Logger.getInstance(QilletniProjectDependencies.class);
    public static final String SRC_DIR = "qilletni-src";

    /** Published on the project message bus after the dependencies were recomputed and changed. */
    public static final Topic<Listener> TOPIC = Topic.create("Qilletni dependencies", Listener.class);

    public interface Listener {
        void dependenciesChanged(@NotNull State state);
    }

    /** Snapshot of every Qilletni project in the IDE project. */
    public record State(@NotNull List<QilletniProjectModel> projects) {
        public static final State EMPTY = new State(List.of());

        /** Every library used by any project, without duplicates. */
        public @NotNull List<InstalledQllLibrary> allLibraries() {
            var byArchive = new LinkedHashMap<Path, InstalledQllLibrary>();
            for (var project : projects) {
                for (var lib : project.libraries()) byArchive.putIfAbsent(lib.archivePath(), lib);
            }
            return List.copyOf(byArchive.values());
        }
    }

    private final Project project;
    private final Alarm refreshAlarm;
    private final SimpleModificationTracker tracker = new SimpleModificationTracker();
    private volatile State state = State.EMPTY;
    private volatile boolean computed;
    private volatile @Nullable LocalFileSystem.WatchRequest homeWatch;

    public QilletniProjectDependencies(@NotNull Project project) {
        this.project = project;
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
                        if (events.stream().anyMatch(QilletniProjectDependencies::isRelevant)) refresh();
                    }
                });
    }

    public static QilletniProjectDependencies getInstance(@NotNull Project project) {
        return project.getService(QilletniProjectDependencies.class);
    }

    public @NotNull State getState() {
        return state;
    }

    /** Whether the first computation has finished. */
    public boolean isComputed() {
        return computed;
    }

    /** Incremented every time the dependencies change; use as a {@code CachedValue} dependency. */
    public @NotNull ModificationTracker getModificationTracker() {
        return tracker;
    }

    /** The Qilletni project containing the given file, if any. */
    public @Nullable QilletniProjectModel findProject(@Nullable VirtualFile file) {
        if (file == null) return null;
        for (var model : state.projects()) {
            if (VfsUtilCore.isAncestor(model.rootDir(), file, false)) return model;
        }
        return null;
    }

    /**
     * Libraries visible from the given file: its project's dependencies, or every project's dependencies for files
     * outside a Qilletni project (such as sources of an installed library).
     */
    public @NotNull List<InstalledQllLibrary> librariesFor(@Nullable VirtualFile file) {
        var model = findProject(file);
        return model != null ? model.libraries() : state.allLibraries();
    }

    /** Schedules a recomputation in every open project. */
    public static void refreshAll() {
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            getInstance(project).refresh();
        }
    }

    /** Schedules a recomputation, coalescing bursts of requests. */
    public void refresh() {
        if (project.isDisposed()) return;
        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(this::recompute, 300);
    }

    /** Recomputes on the calling thread, so tests don't have to wait for the alarm. */
    @TestOnly
    public void recomputeNow() {
        recompute();
    }

    private static boolean isRelevant(VFileEvent event) {
        var path = event.getPath();
        var name = path.substring(path.lastIndexOf('/') + 1);
        return name.equals(QilletniManifest.FILE_NAME)
                || name.equals(QilletniLockfile.FILE_NAME)
                || name.equals(SRC_DIR)
                || name.toLowerCase(Locale.ROOT).endsWith(".qll");
    }

    private void recompute() {
        if (project.isDisposed()) return;
        State newState;
        try {
            newState = new State(findSrcRoots().stream().map(this::computeProject).toList());
        } catch (RuntimeException e) {
            LOG.warn("Failed to compute Qilletni dependencies", e);
            return;
        }
        watchPackageDirectories(newState);

        var oldState = state;
        computed = true;
        if (newState.equals(oldState)) return;
        state = newState;
        tracker.incModificationCount();
        QilletniAliasResolver.onRootsChanged();

        ApplicationManager.getApplication().invokeLater(() -> {
            WriteAction.run(() -> AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                    project, "Qilletni", rootsOf(oldState), rootsOf(newState), "Qilletni libraries"));
            project.getMessageBus().syncPublisher(TOPIC).dependenciesChanged(newState);
            var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(QilletniToolWindowFactory.ID);
            if (toolWindow != null && !newState.projects().isEmpty()) toolWindow.setAvailable(true);
            DaemonCodeAnalyzer.getInstance(project).restart();
            EditorNotifications.getInstance(project).updateAllNotifications();
        }, project.getDisposed());
    }

    private static List<VirtualFile> rootsOf(State state) {
        var roots = new ArrayList<VirtualFile>();
        for (var lib : state.allLibraries()) {
            if (lib.srcRoot() != null && lib.srcRoot().isValid()) roots.add(lib.srcRoot());
            if (lib.nativeJarRoot() != null && lib.nativeJarRoot().isValid()) roots.add(lib.nativeJarRoot());
        }
        return roots;
    }

    /** Finds {@code qilletni-src} directories containing a manifest at the top of each content root. */
    private List<VirtualFile> findSrcRoots() {
        return ReadAction.compute(() -> {
            var candidates = new LinkedHashSet<VirtualFile>();
            var baseDir = ProjectUtil.guessProjectDir(project);
            if (baseDir != null) candidates.add(baseDir);
            for (var module : ModuleManager.getInstance(project).getModules()) {
                candidates.addAll(List.of(ModuleRootManager.getInstance(module).getContentRoots()));
            }
            var srcRoots = new ArrayList<VirtualFile>();
            for (var dir : candidates) {
                var src = dir.findChild(SRC_DIR);
                if (src != null && src.isDirectory() && src.findChild(QilletniManifest.FILE_NAME) != null) srcRoots.add(src);
            }
            return srcRoots;
        });
    }

    private QilletniProjectModel computeProject(VirtualFile srcRoot) {
        var rootDir = srcRoot.getParent();
        var manifestText = readText(srcRoot.findChild(QilletniManifest.FILE_NAME));
        var manifest = manifestText != null ? QilletniManifest.parse(manifestText) : null;
        if (manifest == null) {
            return new QilletniProjectModel(rootDir, srcRoot, null, QilletniProjectModel.Status.INVALID_MANIFEST, List.of());
        }

        var lockText = readText(srcRoot.findChild(QilletniLockfile.FILE_NAME));
        var lockfile = lockText != null ? QilletniLockfile.parse(lockText) : null;
        var libraries = QilletniLibraryManager.getInstance();
        var resolver = new QilletniDependencyResolver(
                List.of(QilletniInstallation.localPackagesDir(), QilletniInstallation.packagesDir()),
                libraries::load, libraries::readInfo);
        var result = resolver.resolve(manifest, lockfile);
        return new QilletniProjectModel(rootDir, srcRoot, manifest, result.status(), result.dependencies());
    }

    /** Makes sure VFS knows about the package directories so installing or deleting a package triggers a refresh. */
    private void watchPackageDirectories(State state) {
        var fs = LocalFileSystem.getInstance();
        var home = QilletniInstallation.home().toString();
        var currentWatch = homeWatch;
        if (currentWatch == null || !currentWatch.getRootPath().equals(home)) {
            if (currentWatch != null) fs.removeWatchedRoot(currentWatch);
            homeWatch = fs.addRootToWatch(home, true);
        }
        for (var model : state.projects()) {
            for (var dep : model.dependencies()) {
                for (var base : List.of(QilletniInstallation.localPackagesDir(), QilletniInstallation.packagesDir())) {
                    var dir = fs.refreshAndFindFileByNioFile(base.resolve(dep.id()));
                    if (dir != null) dir.getChildren();
                }
            }
        }
    }

    private static @Nullable String readText(@Nullable VirtualFile file) {
        if (file == null || !file.isValid()) return null;
        var document = ReadAction.compute(() -> FileDocumentManager.getInstance().getCachedDocument(file));
        if (document != null) return ReadAction.compute(document::getText);
        try {
            return VfsUtilCore.loadText(file);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void dispose() {
        var watch = homeWatch;
        if (watch != null) LocalFileSystem.getInstance().removeWatchedRoot(watch);
    }
}
