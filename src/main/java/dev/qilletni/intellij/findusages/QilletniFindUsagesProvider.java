package dev.qilletni.intellij.findusages;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import dev.qilletni.intellij.QilletniLexerAdapter;
import dev.qilletni.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Find Usages provider for Qilletni identifiers. Scans ID tokens and classifies basic named PSI kinds.
 */
public final class QilletniFindUsagesProvider implements FindUsagesProvider {
    private static final TokenSet IDENTIFIERS = TokenSet.create(QilletniTypes.ID);
    private static final TokenSet COMMENTS = TokenSet.create(QilletniTypes.DOC_COMMENT, QilletniTypes.LINE_COMMENT, QilletniTypes.BLOCK_COMMENT);
    private static final TokenSet STRINGS = TokenSet.create(QilletniTypes.STRING);

    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(new QilletniLexerAdapter(), IDENTIFIERS, COMMENTS, STRINGS);
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiNameIdentifierOwner;
    }

    @Override
    public @Nullable String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        return switch (element) {
            case QilletniFunctionName qilletniFunctionName -> "function";
            case QilletniEntityName qilletniEntityName -> "entity";
            case QilletniPropertyName qilletniPropertyName -> "property";
            case QilletniConstructorName qilletniConstructorName -> "constructor";
            case QilletniVarName qilletniVarName -> "variable";
            case QilletniParamName qilletniParamName -> "parameter";
            default -> "identifier";
        };
    }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        var text = element.getText();
        return text != null ? text : "";
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        var type = getType(element);
        var name = getDescriptiveName(element);
        return type + " " + name;
    }
}
