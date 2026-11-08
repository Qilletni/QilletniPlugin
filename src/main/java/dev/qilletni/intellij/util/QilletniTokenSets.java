package dev.qilletni.intellij.util;

import com.intellij.psi.tree.TokenSet;
import dev.qilletni.intellij.psi.QilletniTypes;

/**
 * Token groupings shared by resolve, completion, indexing and editor features.
 */
public final class QilletniTokenSets {
    private QilletniTokenSets() {}

    /** Tokens that can name a type in a declaration or an extension receiver: built-in type keywords and entity names. */
    public static final TokenSet TYPE_TOKENS = TokenSet.create(
            QilletniTypes.ID,
            QilletniTypes.ANY_TYPE, QilletniTypes.INT_TYPE, QilletniTypes.DOUBLE_TYPE,
            QilletniTypes.STRING_TYPE, QilletniTypes.BOOLEAN_TYPE, QilletniTypes.COLLECTION_TYPE,
            QilletniTypes.SONG_TYPE, QilletniTypes.ALBUM_TYPE, QilletniTypes.WEIGHTS_KEYWORD,
            QilletniTypes.JAVA_TYPE);

    public static final TokenSet COMMENTS = TokenSet.create(
            QilletniTypes.LINE_COMMENT, QilletniTypes.BLOCK_COMMENT, QilletniTypes.DOC_COMMENT);

    /** Whitespace and comments. */
    public static final TokenSet TRIVIA = TokenSet.orSet(TokenSet.WHITE_SPACE, COMMENTS);
}
