package dev.qilletni.intellij.debugger;

import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Turns its type's adapter filter on while the IDE's breakpoint of that type is enabled. */
final class QilletniFilterBreakpointHandler extends XBreakpointHandler<XBreakpoint<XBreakpointProperties<?>>> {
    private final QilletniDebugProcess process;
    private final QilletniFilterBreakpointType type;
    private volatile @Nullable XBreakpoint<XBreakpointProperties<?>> breakpoint;

    QilletniFilterBreakpointHandler(@NotNull QilletniDebugProcess process, @NotNull Class<? extends QilletniFilterBreakpointType> typeClass) {
        super(typeClass);
        this.process = process;
        this.type = XDebuggerUtil.getInstance().findBreakpointType(typeClass);
    }

    @Override
    public void registerBreakpoint(@NotNull XBreakpoint<XBreakpointProperties<?>> breakpoint) {
        this.breakpoint = breakpoint;
        if (process.isConfigured()) process.sendFilterBreakpoints();
    }

    @Override
    public void unregisterBreakpoint(@NotNull XBreakpoint<XBreakpointProperties<?>> breakpoint, boolean temporary) {
        this.breakpoint = null;
        if (process.isConfigured()) process.sendFilterBreakpoints();
    }

    @NotNull QilletniFilterBreakpointType type() {
        return type;
    }

    /** The enabled breakpoint, if any, to report this filter's stops against. */
    @Nullable XBreakpoint<XBreakpointProperties<?>> breakpoint() {
        return breakpoint;
    }
}
