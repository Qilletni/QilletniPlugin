package dev.qilletni.intellij.project;

import com.intellij.ide.wizard.AbstractNewProjectWizardStep;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.dsl.builder.AlignX;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.ui.dsl.builder.Row;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.settings.QilletniConfigurable;
import dev.qilletni.intellij.toolchain.QilletniToolchainLauncher;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Qilletni options in File | New | Project: application or library, scope, author and native bindings. Runs
 * {@code qilletni init} into the new project directory. Everything is validated before the project is created.
 */
public final class QilletniNewProjectWizardStep extends AbstractNewProjectWizardStep {
    private static final Logger LOG = Logger.getInstance(QilletniNewProjectWizardStep.class);
    private static final Pattern NAME_PART = Pattern.compile("[A-Za-z0-9_-]+");

    private final JBRadioButton appRadio = new JBRadioButton(QilletniBundle.message("wizard.type.application"), true);
    private final JBRadioButton libRadio = new JBRadioButton(QilletniBundle.message("wizard.type.library"));
    private final JBTextField scopeField = new JBTextField(sanitize(System.getProperty("user.name", "")), 24);
    private final JBTextField authorField = new JBTextField(System.getProperty("user.name", ""), 24);
    private final JBCheckBox nativeToggle = new JBCheckBox(QilletniBundle.message("wizard.native"));
    private final JBTextField nativeClassField = new JBTextField(24);
    private Row scopeRow;
    private Row nativeClassRow;

    public enum ProjectType {
        APPLICATION("application"),
        LIBRARY("library");

        private final String cliName;

        ProjectType(String cliName) {
            this.cliName = cliName;
        }

        public String getCLIName() {
            return cliName;
        }
    }

    public QilletniNewProjectWizardStep(@NotNull NewProjectWizardStep parent) {
        super(parent);
    }

    @Override
    public void setupUI(@NotNull Panel panel) {
        var group = new ButtonGroup();
        group.add(appRadio);
        group.add(libRadio);
        nativeClassField.getEmptyText().setText("com.example.MyNatives");

        var toolchainProblem = QilletniToolchainLauncher.findProblem(null);
        if (toolchainProblem != null) {
            panel.row("", row -> {
                row.comment("<icon src='AllIcons.General.Warning'>&nbsp;" + toolchainProblem
                        + " <a href='settings'>" + QilletniBundle.message("action.configure") + "</a>", 80,
                        e -> ShowSettingsUtil.getInstance().showSettingsDialog(ProjectManager.getInstance().getDefaultProject(), QilletniConfigurable.class));
                return Unit.INSTANCE;
            });
        }

        panel.row(QilletniBundle.message("wizard.type"), row -> {
            var types = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            types.add(appRadio);
            types.add(libRadio);
            row.cell(types);
            return Unit.INSTANCE;
        });
        scopeRow = panel.row(QilletniBundle.message("wizard.scope"), row -> {
            row.cell(scopeField)
                    .comment(QilletniBundle.message("wizard.scope.hint"), 60, e -> {})
                    .validationOnApply((builder, field) -> libRadio.isSelected() ? validateScope(field.getText()) : null);
            return Unit.INSTANCE;
        });
        panel.row(QilletniBundle.message("wizard.author"), row -> {
            row.cell(authorField).align(AlignX.FILL).resizableColumn()
                    .validationOnApply((builder, field) -> field.getText().isBlank()
                            ? new ValidationInfo(QilletniBundle.message("wizard.author.required"), field) : null);
            return Unit.INSTANCE;
        });
        panel.row("", row -> {
            row.cell(nativeToggle).comment(QilletniBundle.message("wizard.native.hint"), 60, e -> {});
            return Unit.INSTANCE;
        });
        nativeClassRow = panel.row(QilletniBundle.message("wizard.nativeClass"), row -> {
            row.cell(nativeClassField).align(AlignX.FILL).resizableColumn()
                    .validationOnApply((builder, field) -> nativeToggle.isSelected() && !isValidJavaFqcn(field.getText().trim())
                            ? new ValidationInfo(QilletniBundle.message("wizard.nativeClass.invalid"), field) : null);
            return Unit.INSTANCE;
        });

        Runnable updateVisibility = () -> {
            scopeRow.visible(libRadio.isSelected());
            nativeClassRow.visible(nativeToggle.isSelected());
        };
        appRadio.addActionListener(e -> updateVisibility.run());
        libRadio.addActionListener(e -> updateVisibility.run());
        nativeToggle.addActionListener(e -> {
            updateVisibility.run();
            if (nativeToggle.isSelected()) nativeClassField.requestFocusInWindow();
        });
        updateVisibility.run();
    }

    private @Nullable ValidationInfo validateScope(String scope) {
        if (scope.isBlank()) return new ValidationInfo(QilletniBundle.message("wizard.scope.required"), scopeField);
        if (!NAME_PART.matcher(scope.trim()).matches()) return new ValidationInfo(QilletniBundle.message("wizard.scope.invalid"), scopeField);
        return null;
    }

    @Override
    public void setupProject(@NotNull Project project) {
        var baseDir = getContext().getProjectDirectory();
        var name = sanitize(getContext().getProjectName());
        var type = libRadio.isSelected() ? ProjectType.LIBRARY : ProjectType.APPLICATION;
        var qualifiedName = type == ProjectType.LIBRARY ? scopeField.getText().trim() + "/" + name : name;

        var args = new java.util.ArrayList<>(List.of("init", qualifiedName,
                "--dir", baseDir.toAbsolutePath().toString(),
                "--author", authorField.getText().trim(),
                "--type", type.getCLIName()));
        if (nativeToggle.isSelected()) {
            args.add("--native-class");
            args.add(nativeClassField.getText().trim());
        }

        ProgressManager.getInstance().run(new Task.Modal(project, QilletniBundle.message("wizard.progress"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                var result = QilletniProjectInitRunner.run(project, args, indicator);
                var baseVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(baseDir);
                if (baseVf != null) VfsUtil.markDirtyAndRefresh(false, true, true, baseVf);

                if (!result.success()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        var dialog = new QilletniInitLogDialog(QilletniBundle.message("wizard.failed", result.exitCode()));
                        dialog.append(result.output());
                        dialog.show();
                    });
                    return;
                }
                configureProjectRoots(project, baseVf, type);
                QilletniProjectDependencies.getInstance(project).refresh();
                ApplicationManager.getApplication().invokeLater(() -> QilletniPostInitOpener.openPrimaryFile(project, baseVf, type), project.getDisposed());
            }
        });
    }

    private static void configureProjectRoots(@NotNull Project project, @Nullable VirtualFile baseVf, ProjectType type) {
        if (baseVf == null || !baseVf.isDirectory()) return;

        WriteAction.runAndWait(() -> {
            Module module;
            var moduleManager = ModuleManager.getInstance(project);
            var modules = moduleManager.getModules();
            if (modules.length == 0) {
                var basePath = Objects.requireNonNullElse(project.getBasePath(), baseVf.getPath());
                module = moduleManager.newModule(basePath + "/.idea/qilletni.iml", StdModuleTypes.JAVA.getId());
            } else {
                module = modules[0];
            }

            var model = ModuleRootManager.getInstance(module).getModifiableModel();
            try {
                ContentEntry contentEntry = null;
                var baseUrl = VfsUtil.pathToUrl(baseVf.getPath());
                for (var entry : model.getContentEntries()) {
                    var entryFile = entry.getFile();
                    if (entry.getUrl().equals(baseUrl) || entryFile != null && VfsUtil.isAncestor(entryFile, baseVf, false)) {
                        contentEntry = entry;
                        break;
                    }
                }
                if (contentEntry == null) {
                    contentEntry = model.addContentEntry(baseUrl);
                    for (var excluded : List.of(".idea", ".git", "build")) {
                        var child = baseVf.findChild(excluded);
                        if (child != null) contentEntry.addExcludeFolder(child.getUrl());
                    }
                }

                var sourceDirs = type == ProjectType.LIBRARY ? List.of("qilletni-src", "examples") : List.of("qilletni-src");
                for (var sourceDir : sourceDirs) {
                    var dir = baseVf.findChild(sourceDir);
                    if (dir == null || !dir.isDirectory()) continue;
                    var url = dir.getUrl();
                    boolean exists = false;
                    for (var folder : contentEntry.getSourceFolders()) {
                        if (url.equals(folder.getUrl())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) contentEntry.addSourceFolder(url, false);
                }
            } finally {
                model.commit();
            }
        });
    }

    /** Project names become package names, so keep them to letters, digits, '-' and '_'. */
    private static String sanitize(@Nullable String name) {
        if (name == null) return "";
        return name.trim().replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
    }

    private static boolean isValidJavaFqcn(String s) {
        if (s == null || s.isBlank() || s.indexOf('$') >= 0) return false;
        for (var part : s.split("\\.", -1)) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))) return false;
            for (int i = 1; i < part.length(); i++) {
                if (!Character.isJavaIdentifierPart(part.charAt(i))) return false;
            }
        }
        return true;
    }
}
