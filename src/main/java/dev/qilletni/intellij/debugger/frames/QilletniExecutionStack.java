package dev.qilletni.intellij.debugger.frames;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The program's only thread. Frames are fetched in full when the program stops, innermost first. */
final class QilletniExecutionStack extends XExecutionStack {
    private final List<QilletniStackFrame> frames;

    QilletniExecutionStack(@NotNull List<QilletniStackFrame> frames) {
        super(QilletniBundle.message("debugger.thread.main"), AllIcons.Debugger.ThreadSuspended);
        this.frames = frames;
    }

    @Override
    public @Nullable XStackFrame getTopFrame() {
        return frames.isEmpty() ? null : frames.getFirst();
    }

    @Override
    public void computeStackFrames(int firstFrameIndex, @NotNull XStackFrameContainer container) {
        var rest = firstFrameIndex < frames.size() ? frames.subList(firstFrameIndex, frames.size()) : List.<QilletniStackFrame>of();
        container.addStackFrames(rest, true);
    }
}
