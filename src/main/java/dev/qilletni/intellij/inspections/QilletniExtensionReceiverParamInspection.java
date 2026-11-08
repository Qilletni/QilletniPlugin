package dev.qilletni.intellij.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniFunctionDefParams;
import dev.qilletni.intellij.psi.QilletniFunctionOnType;
import dev.qilletni.intellij.psi.QilletniTypes;
import org.jetbrains.annotations.NotNull;

public class QilletniExtensionReceiverParamInspection extends LocalInspectionTool implements DumbAware {
    @Override
    public @NotNull HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    @Override
    public @NotNull String getGroupDisplayName() {
        return "Qilletni";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Extension function must declare receiver parameter";
    }

    @Override
    public @NotNull String getShortName() {
        return "ExtensionReceiverParam";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof QilletniFunctionDef fd)) return;
                QilletniFunctionOnType on = fd.getFunctionOnType();
                if (on == null) return; // not an extension function
                var onType = on.getText().substring(3);
                QilletniFunctionDefParams params = fd.getFunctionDefParams();
                if (params == null) return; // incomplete during typing; avoid NPEs
                // Count param_name occurrences under params
                int count = 0;
                for (PsiElement c = params.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c.getNode() == null) continue;
                    if (c.getNode().getElementType() == QilletniTypes.PARAM_NAME || c.getNode().getElementType() == QilletniTypes.ID) {
                        // param_name is just ID in grammar with mixin; IDs separated by commas
                        count++;
                    }
                }
                if (count == 0) {
                    // Highlight the parentheses range if available; else the params node
                    PsiElement leftParen = fd.getNode().findChildByType(QilletniTypes.LEFT_PAREN) != null ? fd.getNode().findChildByType(QilletniTypes.LEFT_PAREN).getPsi() : null;
                    PsiElement rightParen = fd.getNode().findChildByType(QilletniTypes.RIGHT_PAREN) != null ? fd.getNode().findChildByType(QilletniTypes.RIGHT_PAREN).getPsi() : null;
                    PsiElement target = params;
                    if (leftParen != null && rightParen != null) {
                        target = leftParen;
                    }
                    holder.registerProblem(target,
                            "Extension functions must declare at least one parameter for the receiver type, " + onType,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        };
    }
}
