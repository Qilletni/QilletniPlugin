package dev.qilletni.intellij.debugger;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.QilletniFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Line breakpoints in .ql files. Conditions and log expressions are evaluated by the IDE through the debugger's evaluator. */
public final class QilletniLineBreakpointType extends XLineBreakpointType<XBreakpointProperties<?>> {
    public static final String ID = "qilletni-line";

    public QilletniLineBreakpointType() {
        super(ID, QilletniBundle.message("debugger.breakpoint.line.title"));
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        if (!(file.getFileType() instanceof QilletniFileType)) return false;
        var document = FileDocumentManager.getInstance().getDocument(file);
        var psi = PsiManager.getInstance(project).findFile(file);
        return document != null && psi != null && QilletniBreakpointLines.isBreakable(psi, document, line);
    }

    @Override
    public @Nullable XBreakpointProperties<?> createBreakpointProperties(@NotNull VirtualFile file, int line) {
        return null;
    }
}
