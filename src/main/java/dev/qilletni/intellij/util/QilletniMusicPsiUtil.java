package dev.qilletni.intellij.util;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.psi.QilletniAlbumUrlOrNamePair;
import dev.qilletni.intellij.psi.QilletniCollectionUrlOrNamePair;
import dev.qilletni.intellij.psi.QilletniExpr;
import dev.qilletni.intellij.psi.QilletniSingleWeight;
import dev.qilletni.intellij.psi.QilletniSongUrlOrNamePair;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.psi.QilletniVarDeclaration;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicChoice;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicType;
import dev.qilletni.intellij.spotify.QilletniSpotifyService.MusicTypeContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Utilities for detecting music expression context and applying Spotify selections.
 */
public final class QilletniMusicPsiUtil {

    /**
     * @param targetNode either STRING or *_url_or_name_pair
     */
    public record Context(MusicTypeContext type, PsiElement targetNode) {}

    private QilletniMusicPsiUtil() {}

    public static boolean isMusicNameLeaf(PsiElement element) {
        var parent = element.getParent();
        if (parent == null) {
            return false;
        }

        if (parent.getFirstChild() != element) return false;

        return parent instanceof QilletniSongUrlOrNamePair ||
               parent instanceof QilletniAlbumUrlOrNamePair ||
               parent instanceof QilletniCollectionUrlOrNamePair;
    }

    public static boolean isStringAssignedToMusicType(PsiElement element) {
        var parent = element.getParent();
        if (parent == null) {
            return false;
        }

        if (parent.getFirstChild() != element) return false;

        var containingExpr = PsiTreeUtil.findFirstParent(element, p -> p instanceof QilletniExpr);
        if (containingExpr != null) {
            var exprParent = containingExpr.getParent();
            switch (exprParent) {
                case QilletniVarDeclaration variableDef: {
                    if (variableDef.getFirstChild() != null && variableDef.getFirstChild().getNode() != null) {
                        var variableType = variableDef.getFirstChild().getNode().getElementType();
                        return variableType == QilletniTypes.SONG_TYPE ||
                                variableType == QilletniTypes.ALBUM_TYPE ||
                                variableType == QilletniTypes.COLLECTION_TYPE;
                    }
                    break;
                }
                case QilletniSingleWeight $: return true;
                default: {}
            }
        }

        return false;
    }

    public static Optional<Context> detectContext(@NotNull PsiElement e) {
        PsiElement cur = e;
        while (cur != null && cur.getNode() != null) {
            var type = cur.getNode().getElementType();

            var children = cur.getNode().getChildren(TokenSet.ANY);
            if (children.length == 1 && children[0].getElementType() == QilletniTypes.STRING) { // It is JUST a string, check if it's assigned to a music type

                // This is just a string, so figure out if it's being directly assigned to something
                var containingExpr = PsiTreeUtil.findFirstParent(children[0].getPsi(), p -> p instanceof QilletniExpr);

                if (containingExpr != null) {
                    var exprParent = containingExpr.getParent();

                    switch (exprParent) {
                        case QilletniVarDeclaration variableDef: {
                            if (variableDef.getFirstChild() != null && variableDef.getFirstChild().getNode() != null) {
                                var variableType = variableDef.getFirstChild().getNode().getElementType();
                                if (variableType == QilletniTypes.SONG_TYPE) {
                                    return Optional.of(new Context(MusicTypeContext.SONG, cur));
                                } else if (variableType == QilletniTypes.ALBUM_TYPE) {
                                    return Optional.of(new Context(MusicTypeContext.ALBUM, cur));
                                } else if (variableType == QilletniTypes.COLLECTION_TYPE) {
                                    return Optional.of(new Context(MusicTypeContext.COLLECTION, cur));
                                }
                            }
                            break;
                        }
                        case QilletniSingleWeight singleWeight: {
                            if (PsiTreeUtil.findChildOfType(singleWeight, QilletniSongUrlOrNamePair.class) != null) {
                                return Optional.of(new Context(MusicTypeContext.SONG, cur));
                            } else if (PsiTreeUtil.findChildOfType(singleWeight, QilletniAlbumUrlOrNamePair.class) != null) {
                                return Optional.of(new Context(MusicTypeContext.ALBUM, cur));
                            } else if (PsiTreeUtil.findChildOfType(singleWeight, QilletniCollectionUrlOrNamePair.class) != null) {
                                return Optional.of(new Context(MusicTypeContext.COLLECTION, cur));
                            } else {
                                return Optional.of(new Context(MusicTypeContext.AMBIGUOUS, cur));
                            }
                        }
                        default: {}
                    }
                }
            }

            if (type == QilletniTypes.SONG_URL_OR_NAME_PAIR || type == QilletniTypes.SONG_EXPR) {
                PsiElement target = (type == QilletniTypes.SONG_URL_OR_NAME_PAIR) ? cur : findStringChild(cur, e);
                if (target != null) {
                    return Optional.of(new Context(MusicTypeContext.SONG, target));
                }
            }
            if (type == QilletniTypes.ALBUM_URL_OR_NAME_PAIR || type == QilletniTypes.ALBUM_EXPR) {
                PsiElement target = (type == QilletniTypes.ALBUM_URL_OR_NAME_PAIR) ? cur : findStringChild(cur, e);
                if (target != null) return Optional.of(new Context(MusicTypeContext.ALBUM, target));
            }
            if (type == QilletniTypes.COLLECTION_URL_OR_NAME_PAIR || type == QilletniTypes.COLLECTION_EXPR) {
                PsiElement target = (type == QilletniTypes.COLLECTION_URL_OR_NAME_PAIR) ? cur : findStringChild(cur, e);
                if (target != null) return Optional.of(new Context(MusicTypeContext.COLLECTION, target));
            }
            cur = cur.getParent();
        }
        return Optional.empty();
    }

    private static @Nullable PsiElement findStringChild(PsiElement exprNode, PsiElement near) {
        var strNode = exprNode.getNode().findChildByType(QilletniTypes.STRING);
        if (strNode != null) {
            return strNode.getPsi();
        }

        return null;
    }

    public static void applySelection(@NotNull Project project,
                                      @NotNull Editor editor,
                                      @NotNull Context ctx,
                                      @NotNull MusicChoice choice,
                                      boolean includeSongKeyword) {
        String replacement = buildReplacementText(MusicType.fromContext(ctx.type), choice, includeSongKeyword);
        var doc = editor.getDocument();
        var range = ctx.targetNode.getTextRange();
        WriteCommandAction.runWriteCommandAction(project, "Apply Spotify Selection", null, () -> {
            doc.replaceString(range.getStartOffset(), range.getEndOffset(), replacement);
            editor.getCaretModel().moveToOffset(range.getStartOffset() + replacement.length());
            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        });
    }

    public static String buildReplacementText(@NotNull MusicType type, @NotNull MusicChoice choice, boolean includeSongKeyword) {
        String name = escape(choice.name());
        String primaryArtistOrOwner;
        if (type == MusicType.COLLECTION) {
            primaryArtistOrOwner = choice.owner() != null ? escape(choice.owner()) : "";
        } else {
            if (choice.artists() != null && !choice.artists().isEmpty()) {
                primaryArtistOrOwner = escape(choice.artists().getFirst());
            } else {
                primaryArtistOrOwner = "";
            }
        }

        return switch (choice.type()) {
            case SONG -> {
                String mid = includeSongKeyword ? " song " : " ";
                yield "\"" + name + "\"" + mid + "by \"" + primaryArtistOrOwner + "\"";
            }
            case ALBUM -> "\"" + name + "\" album by \"" + primaryArtistOrOwner + "\"";
            case COLLECTION -> "\"" + name + "\" collection by \"" + primaryArtistOrOwner + "\"";
        };
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }
}
