package dev.qilletni.intellij.settings;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.debugger.QilletniDebuggerSettings;
import dev.qilletni.intellij.library.QilletniProjectDependencies;
import dev.qilletni.intellij.toolchain.QilletniInstallation;
import dev.qilletni.intellij.toolchain.QilletniToolchainService;
import dev.qilletni.intellij.toolchain.QilletniToolchainSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Settings | Languages &amp; Frameworks | Qilletni: where Qilletni is installed and which JDK runs it. Every field is
 * optional; blank fields use what {@link QilletniInstallation} detects, shown as placeholder text.
 */
public final class QilletniConfigurable implements Configurable {
    public static final String ID = "dev.qilletni.intellij.settings";
    /** Combo box entry meaning "no explicit JDK": use the Project SDK or the newest suitable JDK. */
    private static final String AUTO_JDK = "";

    private final Project project;
    private TextFieldWithBrowseButton homeField;
    private TextFieldWithBrowseButton jarField;
    private ComboBox<String> jdkCombo;
    private JBLabel statusLabel;
    private ActionLink installLink;
    private JBIntSpinner weightSongLimit;

    public QilletniConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return QilletniBundle.message("settings.qilletni.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        homeField = new TextFieldWithBrowseButton();
        homeField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(QilletniBundle.message("settings.qilletni.home.title")));
        placeholder(homeField, QilletniInstallation.defaultHome().toString());

        jarField = new TextFieldWithBrowseButton();
        jarField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
                .withTitle(QilletniBundle.message("settings.qilletni.jar.title")));

        var jdkNames = new ArrayList<String>();
        jdkNames.add(AUTO_JDK);
        for (var sdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) jdkNames.add(sdk.getName());
        jdkCombo = new ComboBox<>(jdkNames.toArray(String[]::new));
        jdkCombo.setRenderer(SimpleListCellRenderer.create((label, name, index) -> {
            if (name == null || name.isEmpty()) {
                label.setText(QilletniBundle.message("settings.qilletni.jdk.auto"));
            } else {
                var sdk = ProjectJdkTable.getInstance().findJdk(name);
                label.setText(name);
                if (sdk != null && !QilletniInstallation.isSupportedJdk(sdk)) {
                    label.setText(QilletniBundle.message("settings.qilletni.jdk.tooOld", name, QilletniInstallation.MIN_JDK.getDescription()));
                }
            }
        }));

        statusLabel = new JBLabel();
        statusLabel.setAllowAutoWrapping(true);
        installLink = new ActionLink(QilletniBundle.message("settings.qilletni.install"), (ActionListener) e -> BrowserUtil.browse("https://qilletni.dev/"));
        var checkLink = new ActionLink(QilletniBundle.message("settings.qilletni.check"), (ActionListener) e -> {
            apply();
            refreshStatus();
        });
        var statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusRow.add(statusLabel);
        statusRow.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(12)));
        statusRow.add(checkLink);
        statusRow.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(12)));
        statusRow.add(installLink);

        weightSongLimit = new JBIntSpinner(QilletniDebuggerSettings.DEFAULT_WEIGHT_SONG_LIMIT, 0, 100_000);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(QilletniBundle.message("settings.qilletni.home"), homeField)
                .addTooltip(QilletniBundle.message("settings.qilletni.home.hint"))
                .addLabeledComponent(QilletniBundle.message("settings.qilletni.jar"), jarField)
                .addTooltip(QilletniBundle.message("settings.qilletni.jar.hint"))
                .addLabeledComponent(QilletniBundle.message("settings.qilletni.jdk"), jdkCombo)
                .addTooltip(QilletniBundle.message("settings.qilletni.jdk.hint", QilletniInstallation.MIN_JDK.getDescription()))
                .addVerticalGap(JBUI.scale(8))
                .addLabeledComponent(QilletniBundle.message("settings.qilletni.status"), statusRow)
                .addComponent(new TitledSeparator(QilletniBundle.message("settings.qilletni.debugger")))
                .addLabeledComponent(QilletniBundle.message("settings.qilletni.weightSongLimit"), weightSongLimit)
                .addTooltip(QilletniBundle.message("settings.qilletni.weightSongLimit.hint"))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private static void placeholder(TextFieldWithBrowseButton field, @Nullable String text) {
        if (field.getTextField() instanceof JBTextField textField) {
            textField.getEmptyText().setText(text != null ? QilletniBundle.message("settings.qilletni.detected", text) : QilletniBundle.message("settings.qilletni.notDetected"));
        }
    }

    private void updateJarPlaceholder() {
        var home = homeField.getText().isBlank() ? QilletniInstallation.defaultHome() : Path.of(homeField.getText().trim());
        var detected = QilletniInstallation.detectToolchainJar(home);
        placeholder(jarField, detected != null ? detected.toString() : null);
    }

    private void refreshStatus() {
        statusLabel.setText(QilletniBundle.message("settings.qilletni.status.checking"));
        statusLabel.setForeground(UIUtil.getContextHelpForeground());
        installLink.setVisible(false);
        QilletniToolchainService.getInstance(project).checkAsync().thenAccept(status ->
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (status.ok()) {
                        var jdk = QilletniInstallation.jdk(project);
                        statusLabel.setText(QilletniBundle.message("settings.qilletni.status.ok", status.version(), jdk != null ? jdk.getName() : "?"));
                        statusLabel.setForeground(UIUtil.getLabelSuccessForeground());
                    } else {
                        statusLabel.setText(status.problem());
                        statusLabel.setForeground(UIUtil.getErrorForeground());
                        installLink.setVisible(QilletniInstallation.toolchainJar() == null);
                    }
                }, ModalityState.any()));
    }

    @Override
    public boolean isModified() {
        var settings = QilletniToolchainSettings.getInstance();
        return !Objects.equals(blankToNull(homeField.getText()), blankToNull(settings.getQilletniHome()))
                || !Objects.equals(blankToNull(jarField.getText()), blankToNull(settings.getToolchainPath()))
                || !Objects.equals(blankToNull((String) jdkCombo.getSelectedItem()), blankToNull(settings.getJdkName()))
                || weightSongLimit.getNumber() != QilletniDebuggerSettings.getInstance().getWeightSongLimit();
    }

    @Override
    public void apply() {
        var settings = QilletniToolchainSettings.getInstance();
        var homeChanged = !Objects.equals(blankToNull(homeField.getText()), blankToNull(settings.getQilletniHome()));
        settings.setQilletniHome(blankToNull(homeField.getText()));
        settings.setToolchainPath(blankToNull(jarField.getText()));
        settings.setJdkName(blankToNull((String) jdkCombo.getSelectedItem()));
        QilletniDebuggerSettings.getInstance().setWeightSongLimit(weightSongLimit.getNumber());
        updateJarPlaceholder();
        if (homeChanged) QilletniProjectDependencies.refreshAll();
    }

    @Override
    public void reset() {
        var settings = QilletniToolchainSettings.getInstance();
        homeField.setText(Objects.requireNonNullElse(settings.getQilletniHome(), ""));
        jarField.setText(Objects.requireNonNullElse(settings.getToolchainPath(), ""));
        jdkCombo.setSelectedItem(Objects.requireNonNullElse(settings.getJdkName(), AUTO_JDK));
        weightSongLimit.setNumber(QilletniDebuggerSettings.getInstance().getWeightSongLimit());
        updateJarPlaceholder();
        refreshStatus();
    }

    private static @Nullable String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
