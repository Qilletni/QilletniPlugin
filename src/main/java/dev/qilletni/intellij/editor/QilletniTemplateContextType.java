package dev.qilletni.intellij.editor;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import dev.qilletni.intellij.QilletniFile;
import org.jetbrains.annotations.NotNull;

/** Live template context for .ql files. */
public final class QilletniTemplateContextType extends TemplateContextType {
    public QilletniTemplateContextType() {
        super("Qilletni");
    }

    @Override
    public boolean isInContext(@NotNull TemplateActionContext context) {
        return context.getFile() instanceof QilletniFile;
    }
}
