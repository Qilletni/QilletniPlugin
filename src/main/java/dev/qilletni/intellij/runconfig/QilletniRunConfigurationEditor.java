package dev.qilletni.intellij.runconfig;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.execution.QilletniLogStream;
import org.jetbrains.annotations.NotNull;

final class QilletniRunConfigurationEditor extends QilletniRunConfigurationEditorBase<QilletniRunConfiguration> {
    private final TextFieldWithBrowseButton localLibrary = new TextFieldWithBrowseButton();
    private final JBCheckBox useNativeJar = new JBCheckBox(QilletniBundle.message("runconfig.field.useNativeJar"));
    private final ComboBox<QilletniLogStream.Level> logLevel =
            new ComboBox<>(new QilletniLogStream.Level[]{QilletniLogStream.Level.INFO, QilletniLogStream.Level.DEBUG});

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
        logLevel.setRenderer(SimpleListCellRenderer.create((label, level, index) -> {
            if (level != null) label.setText(QilletniBundle.message(level == QilletniLogStream.Level.DEBUG
                    ? "runconfig.logLevel.debug" : "runconfig.logLevel.info"));
        }));
        form.addLabeledComponent(QilletniBundle.message("runconfig.field.localLibrary"), localLibrary)
                .addComponentToRightColumn(useNativeJar)
                .addTooltip(QilletniBundle.message("runconfig.field.localLibrary.hint"))
                .addLabeledComponent(QilletniBundle.message("runconfig.field.logLevel"), logLevel);
    }

    @Override
    protected void resetFields(@NotNull QilletniRunConfiguration s) {
        localLibrary.setText(s.localLibrary);
        useNativeJar.setSelected(s.useNativeJar);
        useNativeJar.setEnabled(!s.localLibrary.isBlank());
        logLevel.setSelectedItem(s.logLevel);
    }

    @Override
    protected void applyFields(@NotNull QilletniRunConfiguration s) {
        s.localLibrary = localLibrary.getText().trim();
        s.useNativeJar = useNativeJar.isSelected();
        s.logLevel = logLevel.getItem();
    }
}
