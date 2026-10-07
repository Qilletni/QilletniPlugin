package dev.qilletni.intellij.hints;

import com.intellij.codeInsight.hints.declarative.InlayHintsCollector;
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider;
import com.intellij.codeInsight.hints.declarative.InlayTreeSink;
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition;
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniFunctionDefParams;
import dev.qilletni.intellij.psi.QilletniParamName;
import dev.qilletni.intellij.resolve.QilletniParamTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Declarative inlay hints provider that shows the receiver type for extension functions
 * immediately after the first parameter name as a suffix ": <type>".
 */
public final class QilletniExtensionReceiverDeclarativeHintsProvider implements InlayHintsProvider, DumbAware {

    @Override
    public @Nullable InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
        if (!(file instanceof QilletniFile)) return null;
        return new SharedBypassCollector() {
            @Override
            public void collectFromElement(@NotNull PsiElement psiElement, @NotNull InlayTreeSink sink) {
                if (!(psiElement instanceof QilletniFunctionDef fd)) return;
                if (fd.getFunctionOnType() == null) return;
                QilletniFunctionDefParams params = fd.getFunctionDefParams();
                if (params == null) return;
                QilletniParamName firstParam = PsiTreeUtil.findChildOfType(params, QilletniParamName.class);
                if (firstParam == null || firstParam.getId() == null) return;
                String typeText = QilletniParamTypeUtil.getOnTypeCached(fd).orElse(null);
                if (typeText == null) return;
                int offset = firstParam.getId().getTextRange().getEndOffset();

                // Build a simple text presentation ": <type>" with no background
                sink.addPresentation(
                    new InlineInlayPosition(offset, false, 0),
                    null,
                    null,
                    /* hasBackground = */ false,
                    (builder) -> { builder.text(": " + typeText, null); return null; }
                );
            }
        };
    }
}
