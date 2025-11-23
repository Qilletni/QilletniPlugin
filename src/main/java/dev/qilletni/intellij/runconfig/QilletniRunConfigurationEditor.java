package dev.qilletni.intellij.runconfig;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class QilletniRunConfigurationEditor extends SettingsEditor<QilletniRunConfiguration> {
    private final Project project;
    private JPanel panel;
    private TextFieldWithBrowseButton targetPath;
    private JBTextField args;
    private TextFieldWithBrowseButton workingDir;
    private TextFieldWithBrowseButton localLibrary;
    private JCheckBox useNativeJar;
    private EnvironmentVariablesComponent envComponent;

    public QilletniRunConfigurationEditor(Project project) {
        this.project = project;
    }

    @Override
    protected void resetEditorFrom(@NotNull QilletniRunConfiguration s) {
        targetPath.setText(s.targetPath);
        args.setText(s.args);
        workingDir.setText(s.workingDir);
        localLibrary.setText(s.localLibrary);
        if (useNativeJar != null) useNativeJar.setSelected(s.useNativeJar);
        envComponent.setEnvs(s.env);
    }

    @Override
    protected void applyEditorTo(@NotNull QilletniRunConfiguration s) {
        s.targetPath = targetPath.getText();
        s.args = args.getText();
        s.workingDir = workingDir.getText();
        s.localLibrary = localLibrary.getText();
        s.useNativeJar = useNativeJar.isSelected();
        s.env = envComponent.getEnvs();
    }

    @Override
    protected @NotNull JComponent createEditor() {
        panel = new JPanel(new GridBagLayout());
        targetPath = new TextFieldWithBrowseButton();
        targetPath.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor("ql").withTitle("Select target (.ql or directory)"));
        args = new JBTextField();
        workingDir = new TextFieldWithBrowseButton();
        workingDir.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Working Directory"));
        localLibrary = new TextFieldWithBrowseButton();
        localLibrary.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Local library"));
        envComponent = new EnvironmentVariablesComponent();
        useNativeJar = new JCheckBox("Use Native Jar");

        var row = 0;
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Target path:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; panel.add(targetPath, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; panel.add(new JLabel("Program arguments:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; panel.add(args, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; panel.add(new JLabel("Working directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; panel.add(workingDir, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; panel.add(new JLabel("Local library:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; panel.add(localLibrary, gbc); row++;

        // Checkboxes (right column, fill horizontally)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; panel.add(useNativeJar, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; panel.add(new JLabel("Environment:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; panel.add(envComponent, gbc);

        return panel;
    }
}
