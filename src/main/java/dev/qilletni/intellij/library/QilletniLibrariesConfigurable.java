package dev.qilletni.intellij.library;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings page to select the root folder containing .qll archives in hierarchical structure.
 * Expected structure: <root>/<scope>/<name>/<version>.qll
 */
public final class QilletniLibrariesConfigurable implements Configurable {

    private TextFieldWithBrowseButton pathField;
    private JBLabel statusLabel;
    private JPanel root;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Qilletni: Libraries";
    }

    @Override
    public @Nullable JComponent createComponent() {
        pathField = new TextFieldWithBrowseButton();
        com.intellij.openapi.fileChooser.FileChooserDescriptor d = new com.intellij.openapi.fileChooser.FileChooserDescriptor(false, true, false, false, false, false);
        d.setTitle("Select Qilletni Libraries Root");
        pathField.addBrowseFolderListener(new com.intellij.openapi.ui.TextBrowseFolderListener(d));

        statusLabel = new JBLabel(" ");
        var rescan = new JButton("Rescan Now");
        rescan.addActionListener(e -> QilletniLibraryManager.getInstance().rescan());

        var panel = new JBPanel<>(new GridBagLayout());
        var c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 0; c.insets = new Insets(4,4,4,4); c.anchor = GridBagConstraints.WEST;
        panel.add(new JBLabel("Libraries root (folder containing <scope>/<name>/<version>.qll files):"), c);
        c.gridx = 1; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(pathField, c);
        c.gridx = 2; c.gridy = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(rescan, c);
        c.gridx = 1; c.gridy = 1; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(statusLabel, c);

        root = panel;
        return root;
    }

    @Override
    public boolean isModified() {
        var settings = QilletniLibrariesSettings.getInstance();
        var current = settings.getLibrariesRootPath();
        var text = pathField != null ? pathField.getText().trim() : "";
        return !text.equals(current);
    }

    @Override
    public void apply() {
        var text = pathField != null ? pathField.getText().trim() : "";
        QilletniLibrariesSettings.getInstance().setLibrariesRootPath(text);
        // Trigger a rescan upon apply
        QilletniLibraryManager.getInstance().rescan();
    }

    @Override
    public void reset() {
        var settings = QilletniLibrariesSettings.getInstance();
        if (pathField != null) pathField.setText(settings.getLibrariesRootPath());
        updateStatus();
    }

    private void updateStatus() {
        if (statusLabel == null || pathField == null) return;
        String text = pathField.getText().trim();
        if (text.isBlank()) { statusLabel.setText("No libraries root configured."); return; }
        Path p = Path.of(text);
        if (!Files.isDirectory(p)) { statusLabel.setText("Path is not a directory."); return; }
        statusLabel.setText("Ready.");
    }
}
