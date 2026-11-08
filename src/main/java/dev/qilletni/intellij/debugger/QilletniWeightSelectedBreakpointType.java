package dev.qilletni.intellij.debugger;

/**
 * "Weight selected": off by default, maps to the adapter's {@value #FILTER} filter, which stops once per track chosen by
 * weights.
 */
public final class QilletniWeightSelectedBreakpointType extends QilletniFilterBreakpointType {
    public static final String ID = "qilletni-weight-selected";
    public static final String FILTER = "weightSelected";
    public static final String STOP_REASON = "weight selected";

    public QilletniWeightSelectedBreakpointType() {
        super(ID, "debugger.breakpoint.weightSelected.title", "debugger.breakpoint.weightSelected.display", FILTER, STOP_REASON, false);
    }
}
