package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.icons.AllIcons;

// Keybinding + lookup utilities
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniConstructorName;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniEntityInitialize;
import dev.qilletni.intellij.psi.QilletniEntityName;
import dev.qilletni.intellij.psi.QilletniFunctionCall;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniFunctionName;
import dev.qilletni.intellij.psi.QilletniLhsCore;
import dev.qilletni.intellij.psi.QilletniLhsMember;
import dev.qilletni.intellij.psi.QilletniParamName;
import dev.qilletni.intellij.psi.QilletniPostfixExpr;
import dev.qilletni.intellij.psi.QilletniPostfixSuffix;
import dev.qilletni.intellij.psi.QilletniPrimaryExpr;
import dev.qilletni.intellij.psi.QilletniPropertyName;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.psi.QilletniVarDeclaration;
import dev.qilletni.intellij.psi.QilletniVarName;
import dev.qilletni.intellij.resolve.QilletniIndexFacade;
import dev.qilletni.intellij.resolve.QilletniResolveUtil;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicTypeContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.stream.Collectors;

import dev.qilletni.intellij.util.QilletniMusicPsiUtil;
import dev.qilletni.intellij.spotify.QilletniSpotifyService;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicChoice;
import dev.qilletni.intellij.spotify.auth.SpotifyAuthService;

import javax.swing.KeyStroke;

/**
 * Context-aware code completion for Qilletni:
 * - Entities in 'new Foo()' and static access heads 'Foo.'
 * - Members (properties and methods) in postfix chains and assignment LHS
 * - Top-level functions for bare calls
 */
public final class QilletniCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(QilletniCompletionContributor.class);

    private static final class DotAfterExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            PsiElement pos = parameters.getPosition();
            var origFile = parameters.getOriginalFile();
            if (!(origFile instanceof QilletniFile qf)) return;

            // Determine if we are at ".<caret>" position
            boolean atErrorAfterDot = pos.getNode() != null && pos.getNode().getElementType() == TokenType.ERROR_ELEMENT && hasPrecedingDot(pos);
            boolean atDot = pos.getNode() != null && pos.getNode().getElementType() == QilletniTypes.DOT;
            if (!atErrorAfterDot && !atDot) return;

            // Find surrounding postfix expr and its primary (receiver on the left)
            var postfix = PsiTreeUtil.getParentOfType(pos, QilletniPostfixExpr.class);
            if (postfix == null) {
                // When at DOT leaf the parent may be the postfix expr sibling chain; try previous leaf path
                PsiElement prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(pos, true);
                postfix = PsiTreeUtil.getParentOfType(prev, QilletniPostfixExpr.class);
                if (postfix == null) return;
            }
            var primary = PsiTreeUtil.findChildOfType(postfix, QilletniPrimaryExpr.class);
            if (primary == null) return;

            System.out.println("[DEBUG_LOG][Completion] DotAfterExpression detected, primary=" + primary);

            // Properties
            addPropertiesForPrimary(qf, pos, primary, result);
            // Methods (members + extensions)
            addMethodsForPrimary(qf, pos, primary, result);
        }
    }

    // Helper used by dot-after-expression provider
    private static void addPropertiesForPrimary(QilletniFile file, PsiElement contextLeaf, PsiElement primary, CompletionResultSet result) {
        String receiverType = inferReceiverTypeForCompletion(file, primary);
        if (receiverType == null) return;
        var entity = QilletniIndexFacade.findEntityByTypeName(contextLeaf.getProject(), file, receiverType);
        if (entity == null) return;
        for (var p : PsiTreeUtil.findChildrenOfType(entity, QilletniPropertyName.class)) {
            result.addElement(LookupElementBuilder.create(p, p.getText())
                    .withIcon(AllIcons.Nodes.ObjectTypeAttribute)
                    .withTypeText(receiverType, true));
        }
    }

    private static void addMethodsForPrimary(QilletniFile file, PsiElement contextLeaf, PsiElement primary, CompletionResultSet result) {
        String receiverType = inferReceiverTypeForCompletion(file, primary);
        System.out.println("[DEBUG_LOG][Completion] addMethodsForPrimary inferred receiverType=" + receiverType);
        boolean typeReceiver = false;
        if (receiverType == null) {
            PsiElement headId = findDirectIdChild(primary);
            if (headId != null) {
                var ent = QilletniIndexFacade.findEntityByTypeName(contextLeaf.getProject(), file, headId.getText());
                if (ent != null) {
                    receiverType = headId.getText();
                    typeReceiver = true;
                }
            }
        }
        if (receiverType == null) {
            // Unknown type: offer ANY-based extension methods only
            var anyExts = QilletniIndexFacade.listExtensionMethods(contextLeaf.getProject(), file, null);
            for (var def : anyExts) createFunctionLookupElement(result, def);
            return;
        }
        var entity = QilletniIndexFacade.findEntityByTypeName(contextLeaf.getProject(), file, receiverType);
        if (entity != null) {
            for (var def : PsiTreeUtil.findChildrenOfType(entity, QilletniFunctionDef.class)) {
                boolean isStatic = isStaticFunctionDef(def);
                if (typeReceiver) {
                    if (!isStatic) continue;
                } else {
                    if (isStatic) continue;
                }
                createFunctionLookupElement(result, def);
            }
        }
        if (!typeReceiver) {
            var exts = QilletniIndexFacade.listExtensionMethods(contextLeaf.getProject(), file, receiverType);
            for (var def : exts) createFunctionLookupElement(result, def);
        }
    }

    public QilletniCompletionContributor() {
        // Entities in "new Foo()" — be tolerant to intermediate PSI by using super-parent
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID).withSuperParent(2, QilletniEntityInitialize.class),
                new EntitiesCompletionProvider("Basic ID entity init"));

        // Entities at head of a static call: ID under PrimaryExpr with super PostfixExpr
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID)
                        .withParent(QilletniPrimaryExpr.class)
                        .withSuperParent(2, QilletniPostfixExpr.class),
                new EntitiesCompletionProvider("Static call entity init"));

        // Fallback provider: on any ID, compute context and offer entities/members/functions as appropriate
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID),
                new FallbackCompletionProvider());

        // Member properties: ID directly under PostfixSuffix (i.e., `. name`)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID).withParent(QilletniPostfixSuffix.class),
                new MemberPropertiesCompletionProvider());

        // LHS properties: IDs that are direct children of lhs_member
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.ID).withParent(QilletniLhsMember.class),
                new LhsMemberPropertiesCompletionProvider());

        // Dot-after-expression: trigger when caret is at dot or right after a dot
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement()
                        .afterLeaf(PlatformPatterns.psiElement(QilletniTypes.DOT)),
                new DotAfterExpressionCompletionProvider());
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withElementType(TokenType.ERROR_ELEMENT),
                new DotAfterExpressionCompletionProvider());
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(QilletniTypes.DOT),
                new DotAfterExpressionCompletionProvider());

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

    // --- Providers ---

    private static final class EntitiesCompletionProvider extends CompletionProvider<CompletionParameters> {
        private final String label;

        private EntitiesCompletionProvider(String label) {
            this.label = label;
        }

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            System.out.println("========= EntitiesCompletionProvider invoked");
            PsiElement el = parameters.getPosition();
            var origPsiFile = parameters.getOriginalFile();
            if (!(origPsiFile instanceof QilletniFile qf)) {
                return;
            }
            var project = qf.getProject();

            var entities = QilletniIndexFacade.listEntitiesInScope(project, qf);
            for (var e : entities) {
                var nameEl = PsiTreeUtil.findChildOfType(e, QilletniEntityName.class);
                if (nameEl == null) continue;
                result.addElement(LookupElementBuilder.create(e, nameEl.getText())
                        .withIcon(AllIcons.Nodes.Class)
                        .withInsertHandler(ENTITY_CTOR_PARENS_INSERT_HANDLER));
            }
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
        private static final Key<Boolean> SHORTCUTS_INSTALLED_KEY = Key.create("qilletni.spotify.shortcutsInstalled");

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

        // Register Alt+S / Alt+A / Alt+C on the active lookup component (once per popup instance)
        private static void installLookupShortcuts(@NotNull Editor editor) {
            LookupEx lookup = LookupManager.getActiveLookup(editor);
            if (lookup == null) return;
            if (Boolean.TRUE.equals(editor.getUserData(SHORTCUTS_INSTALLED_KEY))) return;

            var comp = editor.getComponent();
            var songs = new FilterAction(MusicTypeContext.SONG);
            var albums = new FilterAction(MusicTypeContext.ALBUM);
            var collections = new FilterAction(MusicTypeContext.COLLECTION);


            songs.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("alt S")), comp);
            albums.registerCustomShortcutSet(CustomShortcutSet.fromString("alt A"), comp);
            collections.registerCustomShortcutSet(CustomShortcutSet.fromString("alt C"), comp);

            // Mark installed on the editor to avoid duplicate registrations
            editor.putUserData(SHORTCUTS_INSTALLED_KEY, Boolean.TRUE);

            // Reset flags when the lookup is closed
            lookup.addLookupListener(new LookupListener() {
                @Override
                public void lookupCanceled(LookupEvent event) {
                    editor.putUserData(SHORTCUTS_INSTALLED_KEY, null);
                    editor.putUserData(FILTER_MODE_KEY, null);
                }
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
                LOG.info("SpotifyMusicCompletion: not signed in, aborting");
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
            result.addLookupAdvertisement("Press Alt+S / Alt+A / Alt+C to filter: Songs / Albums / Collections");
            installLookupShortcuts(parameters.getEditor());

            // Parse text filter optionally (kept as fallback), but keybind filter has precedence
            MusicTypeContext keybindFilter = parameters.getEditor().getUserData(FILTER_MODE_KEY);

            var service = new QilletniSpotifyService();
            java.util.List<MusicChoice> results;
            try {
                // Determine search type: keybind filter > context type > ambiguous
                var baseType = musicCtx.type();
                var searchType = keybindFilter != null ? keybindFilter : baseType;

                results = switch (searchType) {
                    case MusicTypeContext.SONG -> service.searchTracks(query, 12, 0).join();
                    case MusicTypeContext.ALBUM -> service.searchAlbums(query, 12, 0).join();
                    case MusicTypeContext.COLLECTION -> service.searchPlaylists(query, 12, 0).join();
                    case MusicTypeContext.AMBIGUOUS -> service.searchAny(query, 4, 0).join(); // Limit is smaller because this is across all types
                };
            } catch (Exception ex) {
                ex.printStackTrace();
                return; // fail silent in completion
            }
            if (results == null || results.isEmpty()) return;

            // Show results regardless of typed prefix and preserve explicit insertion order via custom weigher
            CompletionSorter sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher())
                    .weighBefore("priority", ORDER_WEIGHER);
            CompletionResultSet rs = result.withRelevanceSorter(sorter).withPrefixMatcher("");

            int index = 0;
            for (var mc : results) {
                String tail = switch (mc.type()) {
                    case SONG, ALBUM -> String.join(", ", mc.artists() == null ? java.util.List.of() : mc.artists());
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
                            var c2 = pos != null ? QilletniMusicPsiUtil.detectContext(pos) : java.util.Optional.<QilletniMusicPsiUtil.Context>empty();
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

    private static final class MemberPropertiesCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            PsiElement idLeaf = com.intellij.codeInsight.completion.CompletionUtil.getOriginalOrSelf(parameters.getPosition());
            var psiFile = idLeaf != null ? idLeaf.getContainingFile() : parameters.getOriginalFile();
            if (!(psiFile instanceof QilletniFile file)) return;

            // Only offer properties if there's a preceding dot or we are under a postfix suffix
            boolean inSuffix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixSuffix.class) != null;
            if (!inSuffix && !hasPrecedingDot(idLeaf)) return;

            var postfix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixExpr.class);
            if (postfix == null) return;
            var primary = PsiTreeUtil.findChildOfType(postfix, QilletniPrimaryExpr.class);

            // Infer receiver type with fallback to variable declaration lookup
            String receiverType = inferReceiverTypeForCompletion((QilletniFile) file, primary);

            // If the base is a type receiver (Foo.), do not propose properties (no static properties yet)
            boolean typeReceiver = false;
            if (receiverType == null) {
                PsiElement headId = findDirectIdChild(primary);
                if (headId != null) {
                    var ent = QilletniIndexFacade.findEntityByTypeName(idLeaf.getProject(), (QilletniFile) file, headId.getText());
                    if (ent != null) {
                        receiverType = headId.getText();
                        typeReceiver = true;
                    }
                }
            }
            if (receiverType == null || typeReceiver) return;

            var entity = QilletniIndexFacade.findEntityByTypeName(idLeaf.getProject(), (QilletniFile) file, receiverType);
            if (entity == null) return;

            for (var p : PsiTreeUtil.findChildrenOfType(entity, QilletniPropertyName.class)) {
                result.addElement(LookupElementBuilder.create(p.getText()));
            }
        }
    }

    private static final class LhsMemberPropertiesCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            PsiElement idLeaf = com.intellij.codeInsight.completion.CompletionUtil.getOriginalOrSelf(parameters.getPosition());
            var psiFile = idLeaf != null ? idLeaf.getContainingFile() : parameters.getOriginalFile();
            if (!(psiFile instanceof QilletniFile file)) return;

            var lhsMember = PsiTreeUtil.getParentOfType(idLeaf, QilletniLhsMember.class);
            if (lhsMember == null) return;
            var lhsCore = PsiTreeUtil.findChildOfType(lhsMember, QilletniLhsCore.class);
            var primary = lhsCore != null ? PsiTreeUtil.findChildOfType(lhsCore, QilletniPrimaryExpr.class) : null;

            // Infer receiver type with fallback to variable declaration lookup
            String receiverType = inferReceiverTypeForCompletion((QilletniFile) file, primary);
            if (receiverType == null) return;

            var entity = QilletniIndexFacade.findEntityByTypeName(idLeaf.getProject(), (QilletniFile) file, receiverType);
            if (entity == null) return;

            for (var p : PsiTreeUtil.findChildrenOfType(entity, QilletniPropertyName.class)) {
                    result.addElement(LookupElementBuilder.create(p, p.getText())
                        .withIcon(AllIcons.Nodes.ObjectTypeAttribute)
                        .withTypeText(receiverType, true));
            }
        }
    }

    private static void createFunctionLookupElement(CompletionResultSet result, QilletniFunctionDef def) {
        var nameEl = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
        if (nameEl == null) return;
        var functionArgs = def.getFunctionDefParams().getParamNameList().stream().map(QilletniParamName::getText).collect(Collectors.joining(", ", "(", ")"));
        result.addElement(LookupElementBuilder.create(def, nameEl.getText())
                .bold()
                .appendTailText(functionArgs, true)
                .withIcon(AllIcons.Nodes.Method)
                .withInsertHandler(CALL_PARENS_INSERT_HANDLER));
    }

    private static final class TopLevelFunctionsCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            System.out.println("========= TopLevelFunctionsCompletionProvider invoked");
            var origFile = parameters.getOriginalFile();
            if (!(origFile instanceof QilletniFile qf)) return;
            var project = qf.getProject();

            System.out.println("\t\tlisting toplevel!");

            var functions = QilletniIndexFacade.listTopLevelFunctions(project, qf);
            for (var f : functions) {
                createFunctionLookupElement(result, f);
            }
        }
    }

    // Fallback: invoked for any ID; detect context and dispatch to proper completion sets.
    private static final class FallbackCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            PsiElement idLeaf = com.intellij.codeInsight.completion.CompletionUtil.getOriginalOrSelf(parameters.getPosition());
            var psiFile = idLeaf != null ? idLeaf.getContainingFile() : parameters.getOriginalFile();
            if (!(psiFile instanceof QilletniFile file)) return;

            // Skip declaration name positions
            var parent = idLeaf.getParent();
            if (parent instanceof QilletniFunctionName
                    || parent instanceof QilletniEntityName
                    || parent instanceof QilletniPropertyName
                    || parent instanceof QilletniConstructorName
                    || parent instanceof QilletniVarName
                    || parent instanceof QilletniParamName) {
                return;
            }

            // Determine context conservatively: treat as member chain only if under a postfix suffix
            boolean inPostfixSuffix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixSuffix.class) != null;
            boolean inLhsMember = PsiTreeUtil.getParentOfType(idLeaf, QilletniLhsMember.class) != null;

            // 1) Postfix member chain: suggest both properties and methods (user may not have typed parentheses yet)
            if (inPostfixSuffix) {
                new MemberPropertiesCompletionProvider().addCompletions(parameters, context, result);
                collectMethodCompletions(idLeaf, result);
                return;
            }

            // 2) LHS assignment member chain (properties only)
            if (inLhsMember) {
                new LhsMemberPropertiesCompletionProvider().addCompletions(parameters, context, result);
                return;
            }

            // 3) Expression head (no chain yet): suggest top-level variables, top-level functions, and entities
            addTopLevelVariableCompletions((QilletniFile) file, result);
            new TopLevelFunctionsCompletionProvider().addCompletions(parameters, context, result);
            new EntitiesCompletionProvider("fallback completions").addCompletions(parameters, context, result);
        }
    }

    // Common utility for method completions (postfix and LHS)
    private static void collectMethodCompletions(PsiElement idLeaf, CompletionResultSet result) {
        System.out.println("[DEBUG_LOG][Completion] collectMethodCompletions start at=" + idLeaf + ", file=" + idLeaf.getContainingFile());
        var file = idLeaf.getContainingFile();
        if (!(file instanceof QilletniFile)) return;

        // Determine receiver type for method calls:
        // - Postfix: require being under a postfix suffix
        // - LHS: primary_expr in lhs_core head
        PsiElement primary = null;

        boolean inSuffix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixSuffix.class) != null;
        System.out.println("[DEBUG_LOG][Completion] inSuffix=" + inSuffix);
        if (!inSuffix) {
            // Not in a proper postfix member; allow LHS methods via lhs_core if needed later
            var lhsCore = PsiTreeUtil.getParentOfType(idLeaf, QilletniLhsCore.class);
            if (lhsCore == null) return;
            primary = PsiTreeUtil.findChildOfType(lhsCore, QilletniPrimaryExpr.class);
        } else {
            var postfix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixExpr.class);
            if (postfix == null) return;
            primary = PsiTreeUtil.findChildOfType(postfix, QilletniPrimaryExpr.class);
        }
        if (primary == null) return;

        // Delegate to shared implementation used by dot-after-expression provider
        addMethodsForPrimary((QilletniFile) file, idLeaf, primary, result);
    }

    // Small helpers (duplicated minimal logic to avoid depending on private resolve utilities)
    private static PsiElement findDirectIdChild(PsiElement owner) {
        if (owner == null) return null;
        for (PsiElement c = owner.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode() != null && c.getNode().getElementType() == QilletniTypes.ID) return c;
        }
        return null;
    }

    private static boolean isStaticFunctionDef(QilletniFunctionDef def) {
        boolean sawStatic = false;
        for (PsiElement c = def.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode() == null) continue;
            var t = c.getNode().getElementType();
            if (t == QilletniTypes.STATIC) {
                sawStatic = true;
            }
            if (t == QilletniTypes.FUNCTION_DEF) {
                return sawStatic;
            }
        }
        return sawStatic;
    }

    // Look back for a DOT token, skipping whitespace and comments
    private static boolean hasPrecedingDot(PsiElement leaf) {
        PsiElement prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(leaf, true);
        while (prev != null) {
            if (prev.getNode() == null) return false;
            var t = prev.getNode().getElementType();
            if (t == TokenType.WHITE_SPACE || t == QilletniTypes.LINE_COMMENT || t == QilletniTypes.BLOCK_COMMENT || t == QilletniTypes.DOC_COMMENT) {
                prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev, true);
                continue;
            }
            return t == QilletniTypes.DOT;
        }
        return false;
    }

    // Look back for a NEW token, skipping whitespace and comments
    private static boolean hasPrecedingNew(PsiElement leaf) {
        if (leaf == null) return false;
        PsiElement prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(leaf, true);
        while (prev != null) {
            if (prev.getNode() == null) return false;
            var t = prev.getNode().getElementType();
            if (t == TokenType.WHITE_SPACE || t == QilletniTypes.LINE_COMMENT || t == QilletniTypes.BLOCK_COMMENT || t == QilletniTypes.DOC_COMMENT) {
                prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev, true);
                continue;
            }
            return t == QilletniTypes.NEW;
        }
        return false;
    }

    // Find previous significant leaf, skipping whitespace/comments and optionally skipping the just-inserted ID once
    private static PsiElement previousSignificantLeafSkippingJustInserted(PsiElement fromLeaf, String justInsertedText) {
        PsiElement prev = fromLeaf;
        // Start from the previous leaf of the given position
        prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev, true);
        boolean skippedInsertedId = false;
        while (prev != null) {
            if (prev.getNode() == null) return null;
            var t = prev.getNode().getElementType();
            if (t == TokenType.WHITE_SPACE || t == QilletniTypes.LINE_COMMENT || t == QilletniTypes.BLOCK_COMMENT || t == QilletniTypes.DOC_COMMENT) {
                prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev, true);
                continue;
            }
            // If the immediate non-trivia token is the just-inserted identifier, skip it once
            if (!skippedInsertedId && t == QilletniTypes.ID && justInsertedText != null && justInsertedText.contentEquals(prev.getText())) {
                skippedInsertedId = true;
                prev = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prev, true);
                continue;
            }
            System.out.println("retting prev = " + prev);
            return prev;
        }
        System.out.println("nulllll");
        return null;
    }

    private static Optional<String> getNativeTypeFromElementType(IElementType type) {
        if (type == QilletniTypes.INT_TYPE) return Optional.of("int");
        if (type == QilletniTypes.DOUBLE_TYPE) return Optional.of("double");
        if (type == QilletniTypes.STRING_TYPE) return Optional.of("string");
        if (type == QilletniTypes.BOOLEAN_TYPE) return Optional.of("boolean");
        if (type == QilletniTypes.COLLECTION_TYPE) return Optional.of("collection");
        if (type == QilletniTypes.SONG_TYPE) return Optional.of("song");
        if (type == QilletniTypes.ALBUM_TYPE) return Optional.of("album");
        if (type == QilletniTypes.WEIGHTS_KEYWORD) return Optional.of("weights");
        return Optional.empty();
    }

    // Infer a receiver type for completion: try static inference, then var-declaration lookup.
    private static String inferReceiverTypeForCompletion(QilletniFile file, PsiElement primary) {
        if (primary == null) return null;
        // Prefer original PSI if available (completion copy may truncate)
        PsiElement orig = com.intellij.codeInsight.completion.CompletionUtil.getOriginalOrSelf(primary);
        if (orig != null) primary = orig;
        String t = dev.qilletni.intellij.resolve.QilletniTypeUtil.inferStaticType(primary);
        if (t != null) return t;

        // If primary is a function call, we can't infer without return type info.
        if (PsiTreeUtil.findChildOfType(primary, QilletniFunctionCall.class) != null) return null;

        // Literal receiver fallback (ensure native-type extension methods on literals work)
        // First check direct children, then recursively, to accommodate completion copies
        for (PsiElement c = primary.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode() == null) continue;
            var stringOptional = getNativeTypeFromElementType(c.getNode().getElementType());
            if (stringOptional.isPresent()) {
                return stringOptional.get();
            }
        }
        for (PsiElement e : PsiTreeUtil.findChildrenOfType(primary, PsiElement.class)) {
            if (e.getNode() == null) continue;
            var stringOptional = getNativeTypeFromElementType(e.getNode().getElementType());
            if (stringOptional.isPresent()) {
                return stringOptional.get();
            }
        }

        // Try resolving a variable used as primary and take its declared type.
        PsiElement idLeaf = findDirectIdChild(primary);
        if (idLeaf != null) {
            PsiElement declName = QilletniResolveUtil.resolveVariableUsage(idLeaf);
            if (declName instanceof QilletniVarName) {
                return getVarDeclarationTypeFromVarName((QilletniVarName) declName);
            }
            if (declName instanceof QilletniParamName pName) {
                var opt = dev.qilletni.intellij.resolve.QilletniParamTypeUtil.getParamType(pName);
                if (opt.isPresent()) return opt.get();
            }
        }
        return null;
    }

    private static String getVarDeclarationTypeFromVarName(QilletniVarName varName) {
        var decl = PsiTreeUtil.getParentOfType(varName, QilletniVarDeclaration.class);
        if (decl == null) return null;
        for (PsiElement c = decl.getFirstChild(); c != null && c != varName; c = c.getNextSibling()) {
            if (c.getNode() == null) continue;
            var tt = c.getNode().getElementType();
            if (tt == QilletniTypes.ID
                    || tt == QilletniTypes.ANY_TYPE || tt == QilletniTypes.INT_TYPE || tt == QilletniTypes.DOUBLE_TYPE
                    || tt == QilletniTypes.STRING_TYPE || tt == QilletniTypes.BOOLEAN_TYPE || tt == QilletniTypes.COLLECTION_TYPE
                    || tt == QilletniTypes.SONG_TYPE || tt == QilletniTypes.ALBUM_TYPE || tt == QilletniTypes.WEIGHTS_KEYWORD
                    || tt == QilletniTypes.JAVA_TYPE) {
                return c.getText();
            }
        }
        return null;
    }

    // Contribute top-level (root-scope) variable names
    private static void addTopLevelVariableCompletions(QilletniFile file, CompletionResultSet result) {
        for (var vName : PsiTreeUtil.findChildrenOfType(file, QilletniVarName.class)) {
            // Only variables not inside any function or entity are considered top-level
            if (PsiTreeUtil.getParentOfType(vName, QilletniFunctionDef.class, false) != null) continue;
            if (PsiTreeUtil.getParentOfType(vName, QilletniEntityDef.class, false) != null) continue;

            String typeText = getVarDeclarationTypeFromVarName(vName);
            var builder = LookupElementBuilder.create(vName.getText())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(typeText != null ? typeText : "var", true);
            result.addElement(builder);
        }
    }

    // Insert handler for function/method items: append "()" if not present and put caret between them
    private static final InsertHandler<LookupElement> CALL_PARENS_INSERT_HANDLER = (context, item) -> {
        var doc = context.getDocument();
        int tail = context.getTailOffset();
        CharSequence seq = doc.getCharsSequence();

        // Skip whitespace after the inserted identifier
        int offset = tail;
        while (offset < seq.length() && Character.isWhitespace(seq.charAt(offset))) {
            offset++;
        }

        // If an opening parenthesis is already present right after the identifier (ignoring whitespace), just place caret inside
        if (offset < seq.length() && seq.charAt(offset) == '(') {
            // Move caret just after '('
            context.getEditor().getCaretModel().moveToOffset(offset + 1);
            return;
        }

        // Otherwise insert "()" at the original tail position and move caret inside the parentheses
        doc.insertString(tail, "()");
        context.getEditor().getCaretModel().moveToOffset(tail + 1);
    };

    // Insert handler for entity constructor names: add parentheses when after `new` or already parsed as entity_initialize
    private static final InsertHandler<LookupElement> ENTITY_CTOR_PARENS_INSERT_HANDLER = (context, item) -> {
        var editor = context.getEditor();
        var file = context.getFile();
        var doc = context.getDocument();
        int tail = context.getTailOffset();

        // Determine context robustly: either already inside a parsed entity_initialize or directly after NEW token
        var caretEl = file.findElementAt(Math.max(0, editor.getCaretModel().getOffset()));
        var beforeTailEl = file.findElementAt(Math.max(0, tail - 1));
        boolean parsedCtor = PsiTreeUtil.getParentOfType(caretEl, QilletniEntityInitialize.class) != null
                || PsiTreeUtil.getParentOfType(beforeTailEl, QilletniEntityInitialize.class) != null;

        // Compute a robust previous significant token before the just-inserted ID text
        String justInserted = item.getLookupString();
        PsiElement scanFrom = beforeTailEl != null ? beforeTailEl : caretEl;
        PsiElement prevSig = scanFrom != null ? previousSignificantLeafSkippingJustInserted(scanFrom, justInserted) : null;
        boolean afterNew = false;
        if (prevSig != null && prevSig.getNode() != null) {
            var tPrev = prevSig.getNode().getElementType();
            afterNew = (tPrev == QilletniTypes.NEW) || hasPrecedingNew(prevSig);
        }
        if (!parsedCtor && !afterNew) return; // Only act in constructor-like context

        CharSequence seq = doc.getCharsSequence();
        int offset = tail;
        while (offset < seq.length() && Character.isWhitespace(seq.charAt(offset))) {
            offset++;
        }
        if (offset < seq.length() && seq.charAt(offset) == '(') {
            editor.getCaretModel().moveToOffset(offset + 1);
            return;
        }
        doc.insertString(tail, "()");
        editor.getCaretModel().moveToOffset(tail + 1);
    };
}
