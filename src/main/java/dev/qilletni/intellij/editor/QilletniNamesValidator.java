package dev.qilletni.intellij.editor;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import dev.qilletni.intellij.QilletniLexerAdapter;
import dev.qilletni.intellij.psi.QilletniTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Lets Rename reject keywords and invalid identifiers, using the lexer as the source of truth. */
public final class QilletniNamesValidator implements NamesValidator {
    @Override
    public boolean isKeyword(@NotNull String name, Project project) {
        var token = singleToken(name);
        return token != null && token != QilletniTypes.ID;
    }

    @Override
    public boolean isIdentifier(@NotNull String name, Project project) {
        return singleToken(name) == QilletniTypes.ID;
    }

    /** The token type if the whole name lexes as exactly one token. */
    private static @Nullable IElementType singleToken(String name) {
        if (name.isEmpty()) return null;
        var lexer = new QilletniLexerAdapter();
        lexer.start(name);
        var type = lexer.getTokenType();
        if (type == null || type == TokenType.BAD_CHARACTER || lexer.getTokenEnd() != name.length()) return null;
        return type;
    }
}
