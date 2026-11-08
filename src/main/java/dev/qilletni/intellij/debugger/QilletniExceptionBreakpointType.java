package dev.qilletni.intellij.debugger;

import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;

/**
 * "Uncaught Qilletni errors": enabled out of the box, maps to the adapter's {@value #FILTER} exception filter.
 */
public final class QilletniExceptionBreakpointType extends QilletniFilterBreakpointType {
    public static final String ID = "qilletni-uncaught";
    public static final String FILTER = "uncaught";

    public QilletniExceptionBreakpointType() {
        super(ID, "debugger.breakpoint.exception.title", "debugger.breakpoint.exception.display", FILTER, StoppedEventArgumentsReason.EXCEPTION, true);
    }
}
