package dev.qilletni.intellij.gutter;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.ui.QilletniMusicPicker;
import dev.qilletni.intellij.util.QilletniMusicPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Gutter icon on music literals that opens the Spotify picker for that literal. */
public final class QilletniMusicLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Register markers only on leaf STRING tokens to avoid performance warnings
        if (element.getNode() == null || element.getNode().getFirstChildNode() != null) return null;
        if (!QilletniMusicPsiUtil.isMusicNameLeaf(element) && !QilletniMusicPsiUtil.isStringAssignedToMusicType(element)) {
            return null;
        }
        if (QilletniMusicPsiUtil.detectContext(element).isEmpty()) return null;

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Search,
                psi -> QilletniBundle.message("spotify.picker.intention"),
                (event, elt) -> {
                    // Use the editor that was clicked, which may not be the selected one in a split view
                    var editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(event.getComponent()));
                    var context = QilletniMusicPsiUtil.detectContext(elt).orElse(null);
                    if (editor != null && context != null) QilletniMusicPicker.pick(elt.getProject(), editor, context);
                },
                GutterIconRenderer.Alignment.CENTER,
                () -> QilletniBundle.message("spotify.picker.intention")
        );
    }
}
