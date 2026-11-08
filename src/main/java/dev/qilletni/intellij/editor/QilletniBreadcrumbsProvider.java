package dev.qilletni.intellij.editor;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import dev.qilletni.intellij.QilletniLanguage;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import org.jetbrains.annotations.NotNull;

/** Shows the enclosing entity and function above the editor. */
public final class QilletniBreadcrumbsProvider implements BreadcrumbsProvider {
    private static final Language[] LANGUAGES = {QilletniLanguage.INSTANCE};

    @Override
    public Language[] getLanguages() {
        return LANGUAGES;
    }

    @Override
    public boolean acceptElement(@NotNull PsiElement element) {
        return element instanceof QilletniEntityDef || element instanceof QilletniFunctionDef;
    }

    @Override
    public @NotNull String getElementInfo(@NotNull PsiElement element) {
        return QilletniStructureElement.nameOf(element);
    }
}
