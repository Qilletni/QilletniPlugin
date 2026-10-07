package dev.qilletni.intellij.runconfig;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;

final class QilletniBuildRunConfigurationEditor extends QilletniRunConfigurationEditorBase<QilletniBuildRunConfiguration> {
    private final TextFieldWithBrowseButton outputDir = new TextFieldWithBrowseButton();
    private final JBCheckBox rebuildNativeJar = new JBCheckBox(QilletniBundle.message("runconfig.field.rebuildNativeJar"));
    private final JBCheckBox verboseGradle = new JBCheckBox(QilletniBundle.message("runconfig.field.verboseGradle"));

    QilletniBuildRunConfigurationEditor(@NotNull Project project) {
        super(project);
    }

    @Override
    protected @NotNull String targetLabel() {
        return QilletniBundle.message("runconfig.field.projectDir");
    }

    @Override
    protected @NotNull FileChooserDescriptor targetDescriptor() {
        return FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(QilletniBundle.message("runconfig.field.projectDir.title"));
    }

    @Override
    protected void addFields(@NotNull FormBuilder form) {
        outputDir.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(QilletniBundle.message("runconfig.field.outputDir.title")));
        form.addLabeledComponent(QilletniBundle.message("runconfig.field.outputDir"), outputDir)
                .addTooltip(QilletniBundle.message("runconfig.field.outputDir.hint"))
                .addComponent(rebuildNativeJar)
                .addComponent(verboseGradle);
    }

    @Override
    protected void resetFields(@NotNull QilletniBuildRunConfiguration s) {
        outputDir.setText(s.outputDir);
        rebuildNativeJar.setSelected(s.rebuildNativeJar);
        verboseGradle.setSelected(s.verboseGradle);
    }

    @Override
    protected void applyFields(@NotNull QilletniBuildRunConfiguration s) {
        s.outputDir = outputDir.getText().trim();
        s.rebuildNativeJar = rebuildNativeJar.isSelected();
        s.verboseGradle = verboseGradle.isSelected();
    }
}
