package dev.qilletni.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import dev.qilletni.intellij.parser.QilletniParser;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.psi.stubs.QilletniFileElementType;
import org.jetbrains.annotations.NotNull;

/**
 * ParserDefinition for Qilletni language wiring generated lexer/parser and PSI.
 */
public final class QilletniParserDefinition implements ParserDefinition {
    private static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
    private static final TokenSet COMMENTS = TokenSet.create(
            QilletniTypes.DOC_COMMENT,
            QilletniTypes.LINE_COMMENT,
            QilletniTypes.BLOCK_COMMENT
    );
    private static final TokenSet STRINGS = TokenSet.create(QilletniTypes.STRING);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new QilletniLexerAdapter();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new QilletniParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return QilletniFileElementType.INSTANCE;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return QilletniTypes.Factory.createElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new QilletniFile(viewProvider);
    }
}
