package dev.qilletni.intellij.debugger;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A breakpoint with no location that maps to one of the adapter's exception breakpoint filters. Each type has exactly
 * one breakpoint, created by default, and does nothing with adapters that don't advertise its filter.
 */
public abstract class QilletniFilterBreakpointType extends XBreakpointType<XBreakpoint<XBreakpointProperties<?>>, XBreakpointProperties<?>> {
    private final String filter;
    private final String stopReason;
    private final String displayKey;
    private final boolean enabledByDefault;

    /**
     * @param stopReason The {@code stopped} reason the adapter uses for this filter
     */
    protected QilletniFilterBreakpointType(@NotNull String id, @NotNull String titleKey, @NotNull String displayKey,
                                           @NotNull String filter, @NotNull String stopReason, boolean enabledByDefault) {
        super(id, QilletniBundle.message(titleKey));
        this.filter = filter;
        this.stopReason = stopReason;
        this.displayKey = displayKey;
        this.enabledByDefault = enabledByDefault;
    }

    public @NotNull String filter() {
        return filter;
    }

    public @NotNull String stopReason() {
        return stopReason;
    }

    @Override
    public @NotNull String getDisplayText(XBreakpoint<XBreakpointProperties<?>> breakpoint) {
        return QilletniBundle.message(displayKey);
    }

    @Override
    public @Nullable XBreakpoint<XBreakpointProperties<?>> createDefaultBreakpoint(@NotNull XBreakpointCreator<XBreakpointProperties<?>> creator) {
        var breakpoint = creator.createBreakpoint(null);
        breakpoint.setEnabled(enabledByDefault);
        return breakpoint;
    }

    @Override
    public boolean isAddBreakpointButtonVisible() {
        return false;
    }
}
