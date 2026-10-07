package dev.qilletni.intellij.runconfig;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/** Editor for the fields every Qilletni configuration has; subclasses add their own rows. */
abstract class QilletniRunConfigurationEditorBase<T extends QilletniRunConfigurationBase> extends SettingsEditor<T> {
    protected final Project project;
    private final TextFieldWithBrowseButton targetPath = new TextFieldWithBrowseButton();
    private final RawCommandLineEditor args = new RawCommandLineEditor();
    private final TextFieldWithBrowseButton workingDir = new TextFieldWithBrowseButton();
    private final EnvironmentVariablesComponent env = new EnvironmentVariablesComponent();

    QilletniRunConfigurationEditorBase(@NotNull Project project) {
        this.project = project;
    }

    protected abstract @NotNull String targetLabel();

    protected abstract @NotNull FileChooserDescriptor targetDescriptor();

    /** Adds configuration-specific rows after the target. */
    protected abstract void addFields(@NotNull FormBuilder form);

    protected abstract void resetFields(@NotNull T configuration);

    protected abstract void applyFields(@NotNull T configuration);

    @Override
    protected final @NotNull JComponent createEditor() {
        targetPath.addBrowseFolderListener(project, targetDescriptor());
        workingDir.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(QilletniBundle.message("runconfig.field.workingDir.title")));
        if (workingDir.getTextField() instanceof JBTextField field) {
            field.getEmptyText().setText(QilletniBundle.message("runconfig.field.workingDir.hint"));
        }

        var form = FormBuilder.createFormBuilder()
                .addLabeledComponent(targetLabel(), targetPath);
        addFields(form);
        return form
                .addLabeledComponent(QilletniBundle.message("runconfig.field.args"), args)
                .addLabeledComponent(QilletniBundle.message("runconfig.field.workingDir"), workingDir)
                .addComponent(env)
                .getPanel();
    }

    @Override
    protected final void resetEditorFrom(@NotNull T s) {
        targetPath.setText(s.targetPath);
        args.setText(s.args);
        workingDir.setText(s.workingDir);
        env.setEnvData(s.env);
        resetFields(s);
    }

    @Override
    protected final void applyEditorTo(@NotNull T s) {
        s.targetPath = targetPath.getText().trim();
        s.args = args.getText();
        s.workingDir = workingDir.getText().trim();
        s.env = env.getEnvData();
        applyFields(s);
    }
}
