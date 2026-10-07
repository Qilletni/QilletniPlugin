package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.psi.QilletniEntityInitialize;
import dev.qilletni.intellij.psi.QilletniLhsMember;
import dev.qilletni.intellij.psi.QilletniPostfixExpr;
import dev.qilletni.intellij.psi.QilletniPostfixSuffix;
import dev.qilletni.intellij.psi.QilletniPrimaryExpr;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.settings.QilletniSpotifyConfigurable;
import dev.qilletni.intellij.spotify.QilletniSpotifyService;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicChoice;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicTypeContext;
import dev.qilletni.intellij.spotify.auth.SpotifyAuthService;
import dev.qilletni.intellij.util.QilletniMusicPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Context-aware code completion for Qilletni:
 * - Entities in 'new Foo()' and static access heads 'Foo.'
 * - Members (properties and methods) in postfix chains and assignment LHS
 * - Top-level functions for bare calls
 * - Spotify music in song, album and collection literals
 */
public final class QilletniCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(QilletniCompletionContributor.class);

    public QilletniCompletionContributor() {
        var entities = new QilletniEntitiesCompletionProvider();

        // Entities in "new Foo()" — be tolerant to intermediate PSI by using super-parent
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID).withSuperParent(2, QilletniEntityInitialize.class),
                entities);

        // Entities at head of a static call: ID under PrimaryExpr with super PostfixExpr
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID)
                        .withParent(QilletniPrimaryExpr.class)
                        .withSuperParent(2, QilletniPostfixExpr.class),
                entities);

        // Fallback provider: on any ID, compute context and offer entities/members/functions as appropriate
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID),
                new QilletniFallbackCompletionProvider());

        // Member properties: ID directly under PostfixSuffix (i.e., `. name`)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID).withParent(QilletniPostfixSuffix.class),
                new QilletniMemberPropertiesCompletionProvider());

        // LHS properties: IDs that are direct children of lhs_member
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID).withParent(QilletniLhsMember.class),
                new QilletniLhsMemberPropertiesCompletionProvider());

        // Dot-after-expression: the provider only acts when the position is a DOT or an error element after one
        extend(CompletionType.BASIC,
                StandardPatterns.or(
                        PlatformPatterns.psiElement(QilletniTypes.DOT),
                        PlatformPatterns.psiElement().withElementType(TokenType.ERROR_ELEMENT)),
                new QilletniDotAfterExpressionCompletionProvider());

        // Spotify inline completion: STRINGs in music expressions (Ctrl+Space only)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.STRING),
                new MusicCompletionProvider());
    }

    @Override
    public void beforeCompletion(@NotNull CompletionInitializationContext context) {
        PsiElement at = context.getFile().findElementAt(context.getStartOffset());
        if (at != null && at.getNode() != null && at.getNode().getElementType() == QilletniTypes.STRING) {
            // Avoid inserting a dummy into string literals so we can extract a clean query
            context.setDummyIdentifier("");
        }
    }

    // Spotify inline completion provider for music strings (Ctrl+Space only)
    private static final class MusicCompletionProvider extends CompletionProvider<CompletionParameters> {
        private static final Key<Integer> ORDER_KEY = Key.create("qilletni.spotify.order");
        private static final LookupElementWeigher ORDER_WEIGHER = new LookupElementWeigher("spotifyOrder") {
            @Override
            public Comparable weigh(LookupElement element) {
                Integer idx = element.getUserData(ORDER_KEY);
                return idx != null ? idx : Integer.MAX_VALUE;
            }
        };

        // Editor-scoped filter mode set by keybindings in the active lookup
        private static final Key<MusicTypeContext> FILTER_MODE_KEY = Key.create("qilletni.spotify.filterMode");
        // Prevent double-registration of shortcuts per lookup instance
        // Lookups that already have the filter shortcuts; only touched on the EDT
        private static final Set<LookupEx> LOOKUPS_WITH_SHORTCUTS = ContainerUtil.createWeakSet();
        private static final Object SIGN_IN_ITEM = new Object();

        // Simple action that toggles a filter and re-triggers completion
        private static final class FilterAction extends AnAction {
            private final MusicTypeContext type;
            FilterAction(MusicTypeContext type) { this.type = type; }
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                var project = e.getProject();
                Editor editor = e.getData(CommonDataKeys.EDITOR);
                if (project == null || editor == null) return;
                editor.putUserData(FILTER_MODE_KEY, type);
                new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor);
            }
        }

        // Register Alt+S / Alt+A / Alt+C on the active lookup (once per popup), for as long as the lookup lives
        private static void installLookupShortcuts(@NotNull Editor editor) {
            ApplicationManager.getApplication().invokeLater(() -> {
                LookupEx lookup = LookupManager.getActiveLookup(editor);
                if (!(lookup instanceof Disposable lookupDisposable)) return;
                if (!LOOKUPS_WITH_SHORTCUTS.add(lookup)) return;

                var component = editor.getComponent();
                new FilterAction(MusicTypeContext.SONG).registerCustomShortcutSet(CustomShortcutSet.fromString("alt S"), component, lookupDisposable);
                new FilterAction(MusicTypeContext.ALBUM).registerCustomShortcutSet(CustomShortcutSet.fromString("alt A"), component, lookupDisposable);
                new FilterAction(MusicTypeContext.COLLECTION).registerCustomShortcutSet(CustomShortcutSet.fromString("alt C"), component, lookupDisposable);
                lookup.addLookupListener(new LookupListener() {
                    @Override
                    public void lookupCanceled(@NotNull LookupEvent event) {
                        editor.putUserData(FILTER_MODE_KEY, null);
                    }

                    @Override
                    public void itemSelected(@NotNull LookupEvent event) {
                        editor.putUserData(FILTER_MODE_KEY, null);
                    }
                });
            });
        }

        /** A lookup item that starts Spotify sign-in instead of inserting text. */
        private static LookupElement signInElement(String text) {
            return LookupElementBuilder.create(SIGN_IN_ITEM, text)
                    .withPresentableText(text)
                    .withIcon(AllIcons.General.User)
                    .withInsertHandler((ctx, item) -> {
                        ctx.getDocument().deleteString(ctx.getStartOffset(), ctx.getTailOffset());
                        var project = ctx.getProject();
                        ApplicationManager.getApplication().invokeLater(() -> {
                            var auth = SpotifyAuthService.getInstance();
                            if (auth.getClientId() == null) {
                                ShowSettingsUtil.getInstance().showSettingsDialog(project, QilletniSpotifyConfigurable.class);
                            } else {
                                auth.signInWithProgress(project, null);
                            }
                        });
                    });
        }

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            // Only on explicit invocation (Ctrl+Space)
            if (parameters.getInvocationCount() <= 0) return;
            PsiElement el = CompletionUtil.getOriginalOrSelf(parameters.getPosition());
            if (el == null || el.getNode() == null || el.getNode().getElementType() != QilletniTypes.STRING) {
                return;
            }
            var ctxOpt = QilletniMusicPsiUtil.detectContext(el);
            if (ctxOpt.isEmpty()) {
                return;
            }
            var musicCtx = ctxOpt.get();
            if (!SpotifyAuthService.getInstance().isSignedIn()) {
                result.withPrefixMatcher("").addElement(signInElement(QilletniBundle.message("spotify.completion.signIn")));
                return;
            }

            // Extract query from STRING token (strip quotes and completion dummy)
            String text = el.getText();
            if (text == null) {
                return;
            }
            // Remove the dummy identifier that may be injected into the completion copy
            text = StringUtil.replace(text, CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "");
            String query = text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"") ? text.substring(1, text.length() - 1) : text;

            query = query.trim();
            if (query.isEmpty()) {
                return;
            }

            // Advertise keyboard filters and install them on the active lookup
            result.addLookupAdvertisement(QilletniBundle.message("spotify.completion.filters"));
            installLookupShortcuts(parameters.getEditor());

            // Parse text filter optionally (kept as fallback), but keybind filter has precedence
            MusicTypeContext keybindFilter = parameters.getEditor().getUserData(FILTER_MODE_KEY);

            var service = new QilletniSpotifyService();
            List<MusicChoice> results;
            try {
                // Determine search type: keybind filter > context type > ambiguous
                var baseType = musicCtx.type();
                var searchType = keybindFilter != null ? keybindFilter : baseType;

                var search = switch (searchType) {
                    case MusicTypeContext.SONG -> service.searchTracks(query, 12, 0);
                    case MusicTypeContext.ALBUM -> service.searchAlbums(query, 12, 0);
                    case MusicTypeContext.COLLECTION -> service.searchPlaylists(query, 12, 0);
                    case MusicTypeContext.AMBIGUOUS -> service.searchAny(query, 4, 0); // Limit is smaller because this is across all types
                };
                // Wait without blocking cancellation, so typing on dismisses the popup immediately
                results = ProgressIndicatorUtils.awaitWithCheckCanceled(search);
            } catch (ProcessCanceledException ex) {
                throw ex;
            } catch (Exception ex) {
                LOG.debug("Spotify completion failed", ex);
                result.addLookupAdvertisement(QilletniBundle.message("spotify.completion.failed"));
                return;
            }
            if (results == null || results.isEmpty()) return;

            // Show results regardless of typed prefix and preserve explicit insertion order via custom weigher
            CompletionSorter sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher())
                    .weighBefore("priority", ORDER_WEIGHER);
            CompletionResultSet rs = result.withRelevanceSorter(sorter).withPrefixMatcher("");

            int index = 0;
            for (var mc : results) {
                String tail = switch (mc.type()) {
                    case SONG, ALBUM -> String.join(", ", mc.artists() == null ? List.of() : mc.artists());
                    case COLLECTION -> mc.owner() == null ? "" : mc.owner();
                };
                String typeText = switch (mc.type()) {
                    case SONG -> "Spotify Song";
                    case ALBUM -> "Spotify Album";
                    case COLLECTION -> "Spotify Collection";
                };
                var builder = LookupElementBuilder.create(mc, mc.name())
                        .withIcon(AllIcons.Actions.Search)
                        .withTailText(tail.isEmpty() ? "" : " — " + tail, true)
                        .withTypeText(typeText, true)
                        .withInsertHandler((insCtx, item) -> {
                            var choice = (MusicChoice) item.getObject();
                            var project = insCtx.getProject();
                            var editor = insCtx.getEditor();
                            PsiElement pos = insCtx.getFile().findElementAt(editor.getCaretModel().getOffset());
                            var c2 = pos != null ? QilletniMusicPsiUtil.detectContext(pos) : Optional.<QilletniMusicPsiUtil.Context>empty();
                            if (c2.isPresent()) {
                                var ctx2 = c2.get();
                                boolean includeSongKeyword = false; // default OFF as requested
                                QilletniMusicPsiUtil.applySelection(project, editor, ctx2, choice, includeSongKeyword);
                            }
                        });
                builder.putUserData(ORDER_KEY, index++);
                rs.addElement(builder);
            }
        }
    }
}
