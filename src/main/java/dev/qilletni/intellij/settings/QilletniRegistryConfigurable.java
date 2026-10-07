package dev.qilletni.intellij.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.packages.QilletniAccountsListener;
import dev.qilletni.intellij.packages.QilletniPackageCommands;
import dev.qilletni.intellij.toolchain.QilletniInstallation;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Settings | Languages &amp; Frameworks | Qilletni | Package Registry: qpm login status. */
public final class QilletniRegistryConfigurable implements Configurable {
    private final Project project;
    private @Nullable Disposable uiDisposable;
    private JBLabel status;
    private JBLabel qpmLocation;

    public QilletniRegistryConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return QilletniBundle.message("settings.registry.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        uiDisposable = Disposer.newDisposable();
        ApplicationManager.getApplication().getMessageBus().connect(uiDisposable)
                .subscribe(QilletniAccountsListener.TOPIC, (QilletniAccountsListener) this::reset);
        status = new JBLabel();
        qpmLocation = new JBLabel();
        qpmLocation.setForeground(UIUtil.getContextHelpForeground());
        var login = new JButton(QilletniBundle.message("qpm.login.action"));
        login.addActionListener(e -> QilletniPackageCommands.login(project));
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(QilletniBundle.message("settings.registry.status"), status)
                .addComponentToRightColumn(login)
                .addTooltip(QilletniBundle.message("settings.registry.hint"))
                .addLabeledComponent(QilletniBundle.message("settings.registry.qpm"), qpmLocation)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public void disposeUIResources() {
        if (uiDisposable != null) Disposer.dispose(uiDisposable);
        uiDisposable = null;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() {
    }

    @Override
    public void reset() {
        boolean loggedIn = QilletniPackageCommands.isLoggedIn();
        status.setText(QilletniBundle.message(loggedIn ? "settings.registry.loggedIn" : "settings.registry.loggedOut"));
        status.setForeground(loggedIn ? UIUtil.getLabelSuccessForeground() : UIUtil.getContextHelpForeground());
        var qpm = QilletniInstallation.qpmLauncher();
        qpmLocation.setText(qpm != null ? qpm.toString() : QilletniBundle.message("toolchain.problem.noQpm"));
    }
}
