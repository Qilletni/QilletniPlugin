package dev.qilletni.intellij.runconfig;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;

final class QilletniRunConfigurationEditor extends QilletniRunConfigurationEditorBase<QilletniRunConfiguration> {
    private final TextFieldWithBrowseButton localLibrary = new TextFieldWithBrowseButton();
    private final JBCheckBox useNativeJar = new JBCheckBox(QilletniBundle.message("runconfig.field.useNativeJar"));

    QilletniRunConfigurationEditor(@NotNull Project project) {
        super(project);
    }

    @Override
    protected @NotNull String targetLabel() {
        return QilletniBundle.message("runconfig.field.script");
    }

    @Override
    protected @NotNull FileChooserDescriptor targetDescriptor() {
        return FileChooserDescriptorFactory.createSingleFileDescriptor("ql").withTitle(QilletniBundle.message("runconfig.field.script.title"));
    }

    @Override
    protected void addFields(@NotNull FormBuilder form) {
        localLibrary.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(QilletniBundle.message("runconfig.field.localLibrary.title")));
        localLibrary.getTextField().getDocument().addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull javax.swing.event.DocumentEvent e) {
                useNativeJar.setEnabled(!localLibrary.getText().isBlank());
            }
        });
        form.addLabeledComponent(QilletniBundle.message("runconfig.field.localLibrary"), localLibrary)
                .addComponentToRightColumn(useNativeJar)
                .addTooltip(QilletniBundle.message("runconfig.field.localLibrary.hint"));
    }

    @Override
    protected void resetFields(@NotNull QilletniRunConfiguration s) {
        localLibrary.setText(s.localLibrary);
        useNativeJar.setSelected(s.useNativeJar);
        useNativeJar.setEnabled(!s.localLibrary.isBlank());
    }

    @Override
    protected void applyFields(@NotNull QilletniRunConfiguration s) {
        s.localLibrary = localLibrary.getText().trim();
        s.useNativeJar = useNativeJar.isSelected();
    }
}
