package dev.qilletni.intellij.settings;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.packages.QilletniAccountsListener;
import dev.qilletni.intellij.spotify.auth.SpotifyAuthService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Settings | Languages &amp; Frameworks | Qilletni | Spotify &amp; Last.fm.
 * <p>
 * The fields are the package configuration in {@code ~/.qilletni/persistence/<package>.properties}, which Qilletni
 * programs read at runtime and {@code qilletni persist} edits. The IDE's own Spotify sign-in, used for music search
 * and completion, reuses the same Client ID and redirect URI.
 */
public final class QilletniSpotifyConfigurable implements Configurable {
    private static final String LASTFM = "lastfm";
    private static final String[] DB_KEYS = {"dbUrl", "dbUsername", "dbPassword"};

    private final Project project;
    private @Nullable Disposable uiDisposable;
    private final Map<String, JTextField> spotifyFields = new LinkedHashMap<>();
    private final Map<String, JTextField> lastFmFields = new LinkedHashMap<>();
    private JBLabel signInStatus;
    private JButton signInButton;
    private JButton signOutButton;

    public QilletniSpotifyConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return QilletniBundle.message("settings.services.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        uiDisposable = Disposer.newDisposable();
        ApplicationManager.getApplication().getMessageBus().connect(uiDisposable)
                .subscribe(QilletniAccountsListener.TOPIC, (QilletniAccountsListener) this::updateSignInState);
        spotifyFields.put("clientId", new JBTextField());
        var redirect = new JBTextField();
        redirect.getEmptyText().setText(SpotifyAuthService.DEFAULT_REDIRECT_URI);
        spotifyFields.put("redirectUri", redirect);
        addDbFields(spotifyFields);
        lastFmFields.put("apiKey", new JBTextField());
        lastFmFields.put("apiSecret", new JBPasswordField());
        addDbFields(lastFmFields);

        signInStatus = new JBLabel();
        signInButton = new JButton(QilletniBundle.message("spotify.signIn"));
        signInButton.addActionListener(e -> signIn());
        signOutButton = new JButton(QilletniBundle.message("spotify.signOut"));
        signOutButton.addActionListener(e -> {
            SpotifyAuthService.getInstance().signOut();
            updateSignInState();
        });
        var signInRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        signInRow.add(signInStatus);
        signInRow.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(12)));
        signInRow.add(signInButton);
        signInRow.add(signOutButton);

        var form = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator(QilletniBundle.message("settings.services.spotify")))
                .addComponent(hint(QilletniBundle.message("settings.services.spotify.hint")))
                .addComponent(new ActionLink(QilletniBundle.message("settings.services.spotify.dashboard"), (ActionListener) e -> BrowserUtil.browse("https://developer.spotify.com/dashboard")))
                .addLabeledComponent(QilletniBundle.message("settings.services.clientId"), spotifyFields.get("clientId"))
                .addLabeledComponent(QilletniBundle.message("settings.services.redirectUri"), spotifyFields.get("redirectUri"))
                .addTooltip(QilletniBundle.message("settings.services.redirectUri.hint"));
        addDbRows(form, spotifyFields);
        form.addLabeledComponent(QilletniBundle.message("settings.services.ideSignIn"), signInRow)
                .addTooltip(QilletniBundle.message("settings.services.ideSignIn.hint"))
                .addComponent(new TitledSeparator(QilletniBundle.message("settings.services.lastfm")))
                .addComponent(hint(QilletniBundle.message("settings.services.lastfm.hint")))
                .addLabeledComponent(QilletniBundle.message("settings.services.apiKey"), lastFmFields.get("apiKey"))
                .addLabeledComponent(QilletniBundle.message("settings.services.apiSecret"), lastFmFields.get("apiSecret"));
        addDbRows(form, lastFmFields);
        return form.addComponentFillVertically(new JPanel(), 0).getPanel();
    }

    private static void addDbFields(Map<String, JTextField> fields) {
        fields.put("dbUrl", new JBTextField());
        fields.put("dbUsername", new JBTextField());
        fields.put("dbPassword", new JBPasswordField());
        ((JBTextField) fields.get("dbUrl")).getEmptyText().setText("jdbc:postgresql://localhost:5432/qilletni");
    }

    private static void addDbRows(FormBuilder form, Map<String, JTextField> fields) {
        form.addLabeledComponent(QilletniBundle.message("settings.services.dbUrl"), fields.get("dbUrl"))
                .addLabeledComponent(QilletniBundle.message("settings.services.dbUsername"), fields.get("dbUsername"))
                .addLabeledComponent(QilletniBundle.message("settings.services.dbPassword"), fields.get("dbPassword"));
    }

    private static JBLabel hint(String text) {
        var label = new JBLabel(text);
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setAllowAutoWrapping(true);
        return label;
    }

    private void signIn() {
        try {
            apply();
        } catch (ConfigurationException e) {
            signInStatus.setText(e.getMessage());
            return;
        }
        var auth = SpotifyAuthService.getInstance();
        if (auth.getClientId() == null) {
            signInStatus.setText(QilletniBundle.message("spotify.signIn.noClientId"));
            signInStatus.setForeground(UIUtil.getErrorForeground());
            return;
        }
        signInButton.setEnabled(false);
        signInStatus.setText(QilletniBundle.message("spotify.status.signingIn"));
        auth.signInWithProgress(project, this::updateSignInState);
    }

    private void updateSignInState() {
        boolean signedIn = SpotifyAuthService.getInstance().isSignedIn();
        signInStatus.setText(QilletniBundle.message(signedIn ? "spotify.status.signedIn" : "spotify.status.signedOut"));
        signInStatus.setForeground(signedIn ? UIUtil.getLabelSuccessForeground() : UIUtil.getContextHelpForeground());
        signInButton.setEnabled(true);
        signInButton.setText(QilletniBundle.message(signedIn ? "spotify.signInAgain" : "spotify.signIn"));
        signOutButton.setVisible(signedIn);
    }

    @Override
    public void disposeUIResources() {
        if (uiDisposable != null) Disposer.dispose(uiDisposable);
        uiDisposable = null;
    }

    @Override
    public boolean isModified() {
        return isModified(SpotifyAuthService.PACKAGE, spotifyFields) || isModified(LASTFM, lastFmFields);
    }

    private static boolean isModified(String pkg, Map<String, JTextField> fields) {
        var stored = QilletniPersistence.load(pkg);
        for (var entry : fields.entrySet()) {
            var value = entry.getValue().getText().trim();
            if (!Objects.equals(value, Objects.requireNonNullElse(stored.getProperty(entry.getKey()), "").trim())) return true;
        }
        return false;
    }

    @Override
    public void apply() throws ConfigurationException {
        try {
            QilletniPersistence.update(SpotifyAuthService.PACKAGE, values(spotifyFields));
            QilletniPersistence.update(LASTFM, values(lastFmFields));
        } catch (IOException e) {
            throw new ConfigurationException(QilletniBundle.message("settings.services.saveFailed", e.getMessage()));
        }
    }

    private static Map<String, String> values(Map<String, JTextField> fields) {
        var values = new LinkedHashMap<String, String>();
        fields.forEach((key, field) -> values.put(key, field.getText()));
        return values;
    }

    @Override
    public void reset() {
        var spotify = QilletniPersistence.load(SpotifyAuthService.PACKAGE);
        spotifyFields.forEach((key, field) -> field.setText(spotify.getProperty(key, "")));
        // Earlier plugin versions kept the Client ID in IDE settings; show it so saving migrates it
        var auth = SpotifyAuthService.getInstance();
        if (spotifyFields.get("clientId").getText().isBlank() && auth.getClientId() != null) {
            spotifyFields.get("clientId").setText(auth.getClientId());
        }
        if (spotifyFields.get("redirectUri").getText().isBlank() && !SpotifyAuthService.DEFAULT_REDIRECT_URI.equals(auth.getRedirectUri())) {
            spotifyFields.get("redirectUri").setText(auth.getRedirectUri());
        }
        var lastFm = QilletniPersistence.load(LASTFM);
        lastFmFields.forEach((key, field) -> field.setText(lastFm.getProperty(key, "")));
        updateSignInState();
    }
}
