package dev.qilletni.intellij.runconfig;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Run icon on the first line of code in a .ql file in the user's project. A Qilletni script runs top to bottom. */
public final class QilletniRunLineMarkerContributor extends RunLineMarkerContributor {
    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        if (element.getFirstChild() != null || element instanceof PsiWhiteSpace || element instanceof PsiComment) return null;
        if (!(element.getContainingFile() instanceof QilletniFile file)) return null;
        var vf = file.getVirtualFile();
        if (vf == null || !vf.isInLocalFileSystem()) return null;
        if (!file.getProject().isInitialized() || !com.intellij.openapi.roots.ProjectFileIndex.getInstance(file.getProject()).isInContent(vf)) return null;

        var first = PsiTreeUtil.firstChild(file);
        while (first != null && (first instanceof PsiWhiteSpace || first instanceof PsiComment || first.getTextLength() == 0)) {
            first = PsiTreeUtil.nextLeaf(first);
        }
        if (first != element) return null;
        return new Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(0), e -> null);
    }
}
