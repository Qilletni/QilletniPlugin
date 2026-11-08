package dev.qilletni.intellij.debugger.frames;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import dev.qilletni.intellij.debugger.QilletniDebugProcess;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** One stop of the program. Qilletni has a single thread, so there is one execution stack. */
public final class QilletniSuspendContext extends XSuspendContext {
    private final QilletniExecutionStack stack;

    public QilletniSuspendContext(@NotNull QilletniDebugProcess process, @NotNull List<QilletniStackFrame> frames) {
        stack = new QilletniExecutionStack(frames);
    }

    @Override
    public @NotNull XExecutionStack getActiveExecutionStack() {
        return stack;
    }

    @Override
    public XExecutionStack @NotNull [] getExecutionStacks() {
        return new XExecutionStack[]{stack};
    }
}
