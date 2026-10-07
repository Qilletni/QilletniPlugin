package dev.qilletni.intellij.project;

import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard;
import dev.qilletni.intellij.QilletniIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * First-class Qilletni entry in the New Project wizard.
 */
public final class QilletniLanguageNewProjectWizard implements LanguageGeneratorNewProjectWizard {
    @Override public @NotNull String getName() { return "Qilletni"; }
    @Override public Icon getIcon() { return QilletniIcons.FILE; }
    @Override public int getOrdinal() { return 100; }

    @Override
    public @NotNull NewProjectWizardStep createStep(@NotNull NewProjectWizardStep parent) {
        return new QilletniNewProjectWizardStep(parent);
    }
}
