package dev.qilletni.intellij.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.actions.QilletniProjectAction;
import dev.qilletni.intellij.library.QilletniDependency;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.library.QilletniProjectModel;
import dev.qilletni.intellij.library.model.QilletniManifest;
import dev.qilletni.intellij.packages.QilletniAccountsListener;
import dev.qilletni.intellij.packages.QilletniPackageCommands;
import dev.qilletni.intellij.settings.QilletniConfigurable;
import dev.qilletni.intellij.settings.QilletniRegistryConfigurable;
import dev.qilletni.intellij.settings.QilletniSpotifyConfigurable;
import dev.qilletni.intellij.spotify.auth.SpotifyAuthService;
import dev.qilletni.intellij.toolchain.QilletniToolchainService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseEvent;

/**
 * The Qilletni tool window: environment status (toolchain, Spotify, package registry) at the top, and each
 * Qilletni project's dependencies with what they resolved to below. Toolbar actions sync, build and publish.
 */
final class QilletniToolWindowPanel extends SimpleToolWindowPanel implements UiDataProvider, Disposable {
    private final Project project;
    private final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    private final StatusRow toolchainRow = new StatusRow();
    private final StatusRow spotifyRow = new StatusRow();
    private final StatusRow registryRow = new StatusRow();

    QilletniToolWindowPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;

        var group = (ActionGroup) ActionManager.getInstance().getAction("Qilletni.ToolWindow.Toolbar");
        var toolbar = ActionManager.getInstance().createActionToolbar("QilletniToolWindow", group, true);
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.getEmptyText().setText(QilletniBundle.message("toolwindow.empty"));
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                return navigate();
            }
        }.installOn(tree);

        var status = FormBuilder.createFormBuilder()
                .addLabeledComponent(QilletniBundle.message("toolwindow.status.toolchain"), toolchainRow)
                .addLabeledComponent(QilletniBundle.message("toolwindow.status.spotify"), spotifyRow)
                .addLabeledComponent(QilletniBundle.message("toolwindow.status.registry"), registryRow)
                .getPanel();
        status.setBorder(JBUI.Borders.empty(6, 8));

        var content = new JPanel(new BorderLayout());
        content.add(status, BorderLayout.NORTH);
        content.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER);
        setContent(content);

        project.getMessageBus().connect(this).subscribe(QilletniProjectDependencies.TOPIC, state -> {
            rebuildTree(state);
            refreshStatus();
        });
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(QilletniAccountsListener.TOPIC, (QilletniAccountsListener) this::refreshStatus);
        // Settings such as the toolchain can change elsewhere; re-read them whenever the panel is shown
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) refreshStatus();
        });

        rebuildTree(QilletniProjectDependencies.getInstance(project).getState());
        refreshStatus();
    }

    /** Re-reads the toolchain, Spotify and registry status. */
    void refreshStatus() {
        toolchainRow.show(QilletniBundle.message("toolwindow.status.checking"), Kind.NEUTRAL, null, null);
        QilletniToolchainService.getInstance(project).checkAsync().thenAccept(status -> ApplicationManager.getApplication().invokeLater(() -> {
            if (status.ok()) {
                toolchainRow.show(status.version(), Kind.OK, null, null);
            } else {
                toolchainRow.show(status.problem(), Kind.ERROR, QilletniBundle.message("action.configure"),
                        () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, QilletniConfigurable.class));
            }
        }, project.getDisposed()));

        var spotify = SpotifyAuthService.getInstance();
        if (spotify.isSignedIn()) {
            spotifyRow.show(QilletniBundle.message("spotify.status.signedIn"), Kind.OK, null, null);
        } else if (spotify.getClientId() == null) {
            spotifyRow.show(QilletniBundle.message("spotify.status.notConfigured"), Kind.NEUTRAL, QilletniBundle.message("action.configure"),
                    () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, QilletniSpotifyConfigurable.class));
        } else {
            spotifyRow.show(QilletniBundle.message("spotify.status.signedOut"), Kind.NEUTRAL, QilletniBundle.message("spotify.signIn"),
                    () -> spotify.signInWithProgress(project, this::refreshStatus));
        }

        if (QilletniPackageCommands.isLoggedIn()) {
            registryRow.show(QilletniBundle.message("settings.registry.loggedIn"), Kind.OK, null, null);
        } else {
            registryRow.show(QilletniBundle.message("settings.registry.loggedOut"), Kind.NEUTRAL, QilletniBundle.message("qpm.login.action"),
                    () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, QilletniRegistryConfigurable.class));
        }
    }

    private void rebuildTree(QilletniProjectDependencies.State state) {
        var root = new DefaultMutableTreeNode();
        for (var model : state.projects()) {
            var projectNode = new DefaultMutableTreeNode(model);
            model.dependencies().stream()
                    .sorted((a, b) -> Boolean.compare(b.isDirect(), a.isDirect()))
                    .forEach(dep -> projectNode.add(new DefaultMutableTreeNode(dep)));
            root.add(projectNode);
        }
        ((DefaultTreeModel) tree.getModel()).setRoot(root);
        TreeUtil.expandAll(tree);
    }

    private @Nullable Object selectedObject() {
        var node = tree.getLastSelectedPathComponent();
        return node instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
    }

    private @Nullable QilletniProjectModel selectedModel() {
        var node = tree.getLastSelectedPathComponent();
        for (var n = node instanceof DefaultMutableTreeNode d ? d : null; n != null; n = (DefaultMutableTreeNode) n.getParent()) {
            if (n.getUserObject() instanceof QilletniProjectModel model) return model;
        }
        return null;
    }

    private boolean navigate() {
        var selected = selectedObject();
        if (selected instanceof QilletniProjectModel model) {
            var manifest = model.srcRoot().findChild(QilletniManifest.FILE_NAME);
            if (manifest != null) FileEditorManager.getInstance(project).openFile(manifest, true);
            return true;
        }
        if (selected instanceof QilletniDependency dep && dep.library() != null && dep.library().srcRoot() != null) {
            ProjectView.getInstance(project).select(null, dep.library().srcRoot(), true);
            return true;
        }
        return false;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        var model = selectedModel();
        if (model != null) sink.set(QilletniProjectAction.MODEL, model);
        VirtualFile file = selectedObject() instanceof QilletniDependency dep && dep.library() != null ? dep.library().srcRoot()
                : model != null ? model.srcRoot().findChild(QilletniManifest.FILE_NAME) : null;
        if (file != null) sink.set(CommonDataKeys.VIRTUAL_FILE, file);
    }

    @Override
    public void dispose() {
    }

    private enum Kind { OK, NEUTRAL, ERROR }

    /** A status text with an optional link that fixes it. */
    private static final class StatusRow extends JPanel {
        private final JBLabel label = new JBLabel();
        private final ActionLink link = new ActionLink("", (ActionListener) e -> {});
        private @Nullable Runnable linkAction;

        StatusRow() {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            label.setAllowAutoWrapping(true);
            link.addActionListener(e -> {
                if (linkAction != null) linkAction.run();
            });
            add(label);
            add(javax.swing.Box.createHorizontalStrut(JBUI.scale(8)));
            add(link);
        }

        void show(@Nullable String text, Kind kind, @Nullable String linkText, @Nullable Runnable action) {
            label.setText(text != null ? text : "");
            label.setIcon(switch (kind) {
                case OK -> AllIcons.General.InspectionsOK;
                case ERROR -> AllIcons.General.Error;
                case NEUTRAL -> null;
            });
            label.setForeground(kind == Kind.ERROR ? UIUtil.getErrorForeground() : UIUtil.getLabelForeground());
            link.setText(linkText != null ? linkText : "");
            link.setVisible(linkText != null);
            linkAction = action;
        }
    }

    private static final class Renderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            var userObject = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : null;
            if (userObject instanceof QilletniProjectModel model) {
                setIcon(dev.qilletni.intellij.QilletniIcons.FILE);
                append(model.displayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                if (model.manifest() != null && model.manifest().version() != null) {
                    append("  " + model.manifest().version(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                var problem = switch (model.status()) {
                    case OK -> null;
                    case NO_LOCKFILE -> QilletniBundle.message("toolwindow.project.noLockfile");
                    case LOCK_STALE -> QilletniBundle.message("toolwindow.project.lockStale");
                    case MISSING_PACKAGES -> QilletniBundle.message("toolwindow.project.missing", model.missing().size());
                    case INVALID_MANIFEST -> QilletniBundle.message("toolwindow.project.invalidManifest");
                };
                if (problem != null) {
                    var attrs = model.status() == QilletniProjectModel.Status.NO_LOCKFILE
                            ? SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES;
                    append("  " + problem, attrs);
                }
            } else if (userObject instanceof QilletniDependency dep) {
                setIcon(dep.isMissing() ? AllIcons.General.Error : dep.problem() != null ? AllIcons.General.Warning : AllIcons.Nodes.PpLib);
                append(dep.id(), dep.isMissing() ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (dep.version() != null) append("  " + dep.version(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                if (dep.isDirect() && dep.requestedRange() != null && !dep.requestedRange().equals(dep.version())) {
                    append("  (" + dep.requestedRange() + ")", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                }
                if (!dep.isDirect()) append("  " + QilletniBundle.message("toolwindow.dependency.transitive"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                if (dep.problem() != null) append("  " + dep.problem(), SimpleTextAttributes.ERROR_ATTRIBUTES);
                if (dep.archive() != null) setToolTipText(dep.archive().toString());
            }
        }
    }
}
