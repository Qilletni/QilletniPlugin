package dev.qilletni.intellij.editor;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.QilletniIcons;
import org.jetbrains.annotations.NotNull;

/** File | New | Qilletni File, with templates for a script and an entity. */
public final class QilletniCreateFileAction extends CreateFileFromTemplateAction implements DumbAware {
    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory, CreateFileFromTemplateDialog.@NotNull Builder builder) {
        builder.setTitle(QilletniBundle.message("newFile.title"))
                .addKind(QilletniBundle.message("newFile.kind.script"), QilletniIcons.FILE, "Qilletni File")
                .addKind(QilletniBundle.message("newFile.kind.entity"), QilletniIcons.FILE, "Qilletni Entity");
    }

    @Override
    protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return QilletniBundle.message("newFile.title");
    }
}
