package dev.qilletni.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.settings.QilletniSpotifyConfigurable;
import dev.qilletni.intellij.spotify.QilletniSpotifyService;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicChoice;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicType;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicTypeContext;
import dev.qilletni.intellij.spotify.auth.SpotifyAuthService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Search Spotify for a song, album or playlist. Searching starts as you type; results can be filtered and sorted
 * locally. When not signed in, the dialog offers to sign in without leaving it.
 */
public class SelectSpotifyDialog extends DialogWrapper {
    private static final int ARTWORK_CACHE_SIZE = 200;

    private enum Sort {
        BEST("spotify.picker.sort.best"), NAME("spotify.picker.sort.name"), YEAR("spotify.picker.sort.year");
        final String key;
        Sort(String key) { this.key = key; }
    }

    private final Project project;
    private final QilletniSpotifyService service = new QilletniSpotifyService();
    private final Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, getDisposable());
    private final AtomicInteger searchGeneration = new AtomicInteger();
    private final Map<String, Icon> artwork = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Icon> eldest) {
            return size() > ARTWORK_CACHE_SIZE;
        }
    };

    private MusicType selectedType;
    private List<MusicChoice> results = List.of();
    private final DefaultListModel<MusicChoice> model = new DefaultListModel<>();
    private final JBList<MusicChoice> list = new JBList<>(model);
    private final JBTextField search = new JBTextField();
    private final JBTextField filter = new JBTextField();
    private final ComboBox<Sort> sort = new ComboBox<>(Sort.values());
    private final ComboBox<MusicType> typeBox = new ComboBox<>(MusicType.values());
    private final JBCheckBox includeSongKeyword = new JBCheckBox(QilletniBundle.message("spotify.picker.songKeyword"), false);
    private final JBLabel message = new JBLabel();
    private final JButton retryButton = new JButton(QilletniBundle.message("spotify.picker.retry"));
    private final JButton signInButton = new JButton(QilletniBundle.message("spotify.signIn"));
    private final JButton settingsButton = new JButton(QilletniBundle.message("spotify.picker.settings"));

    public SelectSpotifyDialog(Project project, MusicTypeContext initialType) {
        super(project, true);
        this.project = project;
        this.selectedType = MusicType.fromContext(initialType);
        setTitle(QilletniBundle.message("spotify.picker.title"));
        init();
        getOKAction().setEnabled(false);
        updateSignInState();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return search;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        typeBox.setSelectedItem(selectedType);
        typeBox.setRenderer(SimpleListCellRenderer.create("", type -> QilletniBundle.message("spotify.picker.type." + type.name().toLowerCase(Locale.ROOT))));
        typeBox.addActionListener(e -> {
            selectedType = (MusicType) typeBox.getSelectedItem();
            includeSongKeyword.setEnabled(selectedType == MusicType.SONG);
            scheduleSearch();
        });
        includeSongKeyword.setEnabled(selectedType == MusicType.SONG);

        search.getEmptyText().setText(QilletniBundle.message("spotify.picker.search"));
        search.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                scheduleSearch();
            }
        });
        filter.getEmptyText().setText(QilletniBundle.message("spotify.picker.filter"));
        filter.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                showResults();
            }
        });
        sort.setRenderer(SimpleListCellRenderer.create("", s -> QilletniBundle.message(s.key)));
        sort.addActionListener(e -> showResults());

        var searchRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        searchRow.add(typeBox, BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);

        var refineRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        refineRow.add(filter, BorderLayout.CENTER);
        refineRow.add(sort, BorderLayout.EAST);

        retryButton.addActionListener(e -> scheduleSearch());
        signInButton.addActionListener(e -> {
            signInButton.setEnabled(false);
            SpotifyAuthService.getInstance().signInWithProgress(project, () -> {
                signInButton.setEnabled(true);
                updateSignInState();
                if (SpotifyAuthService.getInstance().isSignedIn()) scheduleSearch();
            });
        });
        settingsButton.addActionListener(e -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, QilletniSpotifyConfigurable.class);
            updateSignInState();
        });
        var messageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        messageRow.add(message);
        messageRow.add(retryButton);
        messageRow.add(signInButton);
        messageRow.add(settingsButton);

        var north = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        north.add(searchRow, BorderLayout.NORTH);
        north.add(refineRow, BorderLayout.CENTER);
        north.add(messageRow, BorderLayout.SOUTH);

        list.setCellRenderer((lst, value, index, isSelected, cellHasFocus) -> renderChoice(value, isSelected));
        list.getEmptyText().setText("");
        list.addListSelectionListener(e -> getOKAction().setEnabled(list.getSelectedValue() != null));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) doOKAction();
            }
        });
        var scroll = ScrollPaneFactory.createScrollPane(list);
        scroll.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(360)));

        var panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        panel.add(north, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(includeSongKeyword, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent renderChoice(MusicChoice value, boolean isSelected) {
        var root = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        root.setBorder(JBUI.Borders.empty(4));
        var icon = new JLabel(value.imageUrl() != null ? artworkFor(value.imageUrl()) : null);
        icon.setPreferredSize(new Dimension(JBUI.scale(32), JBUI.scale(32)));
        root.add(icon, BorderLayout.WEST);
        var name = new JLabel(value.name());
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        var meta = new JLabel(secondaryText(value) + (value.year() != null ? "  ·  " + value.year() : ""));
        meta.setForeground(isSelected ? UIUtil.getListSelectionForeground(true) : UIUtil.getContextHelpForeground());
        name.setForeground(UIUtil.getListForeground(isSelected, true));
        var text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(name, BorderLayout.NORTH);
        text.add(meta, BorderLayout.SOUTH);
        root.add(text, BorderLayout.CENTER);
        root.setBackground(UIUtil.getListBackground(isSelected, true));
        return root;
    }

    private static String secondaryText(MusicChoice choice) {
        return switch (choice.type()) {
            case SONG, ALBUM -> String.join(", ", choice.artists() == null ? List.of() : choice.artists());
            case COLLECTION -> choice.owner() != null ? choice.owner() : "";
        };
    }

    private Icon artworkFor(String url) {
        synchronized (artwork) {
            if (artwork.containsKey(url)) return artwork.get(url);
            artwork.put(url, null); // loading
        }
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                var bytes = HttpRequests.request(url).connectTimeout(5_000).readTimeout(10_000).readBytes(null);
                var image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) return;
                int size = JBUI.scale(32);
                var icon = new JBImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH));
                synchronized (artwork) {
                    artwork.put(url, icon);
                }
                ApplicationManager.getApplication().invokeLater(list::repaint, ModalityState.any());
            } catch (IOException ignored) {
                // Artwork is decorative; leave the placeholder
            }
        });
        return null;
    }

    private void updateSignInState() {
        var auth = SpotifyAuthService.getInstance();
        if (auth.isSignedIn()) {
            showMessage(null, false);
        } else if (auth.getClientId() == null) {
            showMessage(QilletniBundle.message("spotify.picker.notConfigured"), false);
            settingsButton.setVisible(true);
        } else {
            showMessage(QilletniBundle.message("spotify.picker.notSignedIn"), false);
            signInButton.setVisible(true);
        }
    }

    private void showMessage(@Nullable String text, boolean retry) {
        message.setText(text != null ? text : "");
        message.setVisible(text != null);
        retryButton.setVisible(retry);
        signInButton.setVisible(false);
        settingsButton.setVisible(false);
    }

    private void scheduleSearch() {
        searchAlarm.cancelAllRequests();
        searchAlarm.addRequest(this::performSearch, 300);
    }

    private void performSearch() {
        var query = search.getText().trim();
        int generation = searchGeneration.incrementAndGet();
        if (query.isEmpty()) {
            results = List.of();
            showResults();
            return;
        }
        if (!SpotifyAuthService.getInstance().isSignedIn()) {
            updateSignInState();
            return;
        }
        showMessage(null, false);
        list.setPaintBusy(true);
        CompletableFuture<List<MusicChoice>> future = switch (selectedType) {
            case SONG -> service.searchTracks(query, 25, 0);
            case ALBUM -> service.searchAlbums(query, 25, 0);
            case COLLECTION -> service.searchPlaylists(query, 25, 0);
        };
        future.whenComplete((found, error) -> ApplicationManager.getApplication().invokeLater(() -> {
            // A newer search was started; ignore this one's results
            if (generation != searchGeneration.get()) return;
            list.setPaintBusy(false);
            if (error != null) {
                showError(error);
                return;
            }
            results = found != null ? found : List.of();
            if (results.isEmpty()) showMessage(QilletniBundle.message("spotify.picker.noResults", query), false);
            showResults();
        }, ModalityState.any()));
    }

    private void showError(Throwable error) {
        var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof se.michaelthelin.spotify.exceptions.SpotifyWebApiException) {
            var text = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
            if (text.contains("401") || text.contains("unauthorized") || text.contains("invalid access")) {
                showMessage(QilletniBundle.message("spotify.picker.error.auth"), false);
                signInButton.setVisible(true);
            } else if (text.contains("429") || text.contains("rate limit")) {
                showMessage(QilletniBundle.message("spotify.picker.error.rateLimit"), true);
            } else {
                showMessage(QilletniBundle.message("spotify.picker.error.api"), true);
            }
        } else if (cause instanceof IllegalStateException && !SpotifyAuthService.getInstance().isSignedIn()) {
            updateSignInState();
        } else {
            showMessage(QilletniBundle.message("spotify.picker.error.network"), true);
        }
    }

    /** Applies the local filter and sort to the last search results. */
    private void showResults() {
        var needle = filter.getText().trim().toLowerCase(Locale.ROOT);
        var shown = new ArrayList<>(results);
        if (!needle.isEmpty()) {
            shown.removeIf(choice -> !(String.valueOf(choice.name()).toLowerCase(Locale.ROOT).contains(needle)
                    || secondaryText(choice).toLowerCase(Locale.ROOT).contains(needle)));
        }
        var order = (Sort) sort.getSelectedItem();
        if (order == Sort.NAME) {
            shown.sort(Comparator.comparing(c -> String.valueOf(c.name()), String.CASE_INSENSITIVE_ORDER));
        } else if (order == Sort.YEAR) {
            shown.sort(Comparator.comparing((MusicChoice c) -> c.year() == null ? Integer.MIN_VALUE : c.year()).reversed());
        }
        var previous = list.getSelectedValue();
        model.clear();
        shown.forEach(model::addElement);
        if (previous != null && shown.contains(previous)) list.setSelectedValue(previous, true);
        else if (!shown.isEmpty()) list.setSelectedIndex(0);
        getOKAction().setEnabled(list.getSelectedValue() != null);
    }

    @Override
    protected void doOKAction() {
        if (list.getSelectedValue() == null) return;
        super.doOKAction();
    }

    public Optional<MusicChoice> getSelection() { return Optional.ofNullable(list.getSelectedValue()); }

    public boolean includeKeyword() { return includeSongKeyword.isSelected(); }

    public MusicType getType() { return selectedType; }
}
