package dev.qilletni.intellij.debugger;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import dev.qilletni.intellij.QilletniBundle;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps the adapter's breakpoints in sync with the IDE's. DAP replaces all breakpoints of a source at once, so any
 * change to a file's breakpoints re-sends that file's whole list. Conditions and log expressions are not sent: the
 * IDE evaluates them itself when a breakpoint is hit.
 */
final class QilletniLineBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<?>>> {
    private static final Logger LOG = Logger.getInstance(QilletniLineBreakpointHandler.class);

    private final QilletniDebugProcess process;
    private final Map<String, List<XLineBreakpoint<XBreakpointProperties<?>>>> bySource = new LinkedHashMap<>();
    private final Map<Integer, XLineBreakpoint<XBreakpointProperties<?>>> byAdapterId = new HashMap<>();

    QilletniLineBreakpointHandler(@NotNull QilletniDebugProcess process) {
        super(QilletniLineBreakpointType.class);
        this.process = process;
    }

    @Override
    public void registerBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties<?>> breakpoint) {
        var path = dapPath(breakpoint);
        if (path == null) {
            process.getSession().setBreakpointInvalid(breakpoint, QilletniBundle.message("debugger.breakpoint.unmapped"));
            return;
        }
        synchronized (this) {
            bySource.computeIfAbsent(path, p -> new ArrayList<>()).add(breakpoint);
        }
        if (process.isConfigured()) send(path);
    }

    @Override
    public void unregisterBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties<?>> breakpoint, boolean temporary) {
        var path = dapPath(breakpoint);
        if (path == null) return;
        synchronized (this) {
            var list = bySource.get(path);
            if (list != null) list.remove(breakpoint);
            byAdapterId.values().remove(breakpoint);
        }
        if (process.isConfigured()) send(path);
    }

    /** Sends every source's breakpoints; used once during the configuration phase. */
    @NotNull CompletableFuture<Void> sendAll() {
        List<String> paths;
        synchronized (this) {
            paths = new ArrayList<>(bySource.keySet());
        }
        return CompletableFuture.allOf(paths.stream().map(this::send).toArray(CompletableFuture[]::new));
    }

    private @NotNull CompletableFuture<Void> send(@NotNull String path) {
        List<XLineBreakpoint<XBreakpointProperties<?>>> breakpoints;
        synchronized (this) {
            breakpoints = new ArrayList<>(bySource.getOrDefault(path, List.of()));
        }
        var args = new SetBreakpointsArguments();
        args.setSource(source(path));
        args.setBreakpoints(breakpoints.stream().map(bp -> {
            var sbp = new SourceBreakpoint();
            sbp.setLine(bp.getLine() + 1);
            return sbp;
        }).toArray(SourceBreakpoint[]::new));
        return process.dap().server().setBreakpoints(args).handle((response, error) -> {
            if (error != null) {
                LOG.warn("setBreakpoints failed for " + path + ": " + error);
                return null;
            }
            var results = response != null && response.getBreakpoints() != null ? response.getBreakpoints() : new Breakpoint[0];
            for (int i = 0; i < breakpoints.size() && i < results.length; i++) {
                if (results[i].getId() != null) {
                    synchronized (this) {
                        byAdapterId.put(results[i].getId(), breakpoints.get(i));
                    }
                }
                apply(breakpoints.get(i), results[i]);
            }
            return null;
        });
    }

    /** The adapter changed a breakpoint after the fact, typically a pending one whose file has now been loaded. */
    void breakpointChanged(@NotNull Breakpoint result) {
        var breakpoint = result.getId() != null ? findById(result.getId()) : null;
        if (breakpoint != null) apply(breakpoint, result);
    }

    /** The first IDE breakpoint matching one of the adapter's hit ids. */
    @Nullable XLineBreakpoint<XBreakpointProperties<?>> findHit(@Nullable Integer[] hitIds) {
        if (hitIds == null) return null;
        for (var id : hitIds) {
            var breakpoint = id != null ? findById(id) : null;
            if (breakpoint != null) return breakpoint;
        }
        return null;
    }

    private synchronized @Nullable XLineBreakpoint<XBreakpointProperties<?>> findById(int id) {
        return byAdapterId.get(id);
    }

    /** Verified, invalid (with a message) or pending (unverified without a message: the file isn't loaded yet). */
    private void apply(XLineBreakpoint<XBreakpointProperties<?>> breakpoint, Breakpoint result) {
        var session = process.getSession();
        if (result.isVerified()) {
            session.setBreakpointVerified(breakpoint);
        } else if (result.getMessage() != null && !result.getMessage().isBlank()) {
            session.setBreakpointInvalid(breakpoint, result.getMessage());
        }
    }

    private @Nullable String dapPath(XLineBreakpoint<?> breakpoint) {
        var file = VirtualFileManager.getInstance().findFileByUrl(breakpoint.getFileUrl());
        return file != null ? process.mapper().toDapPath(file) : null;
    }

    static @NotNull Source source(@NotNull String path) {
        var source = new Source();
        source.setPath(path);
        int slash = path.lastIndexOf('/');
        int backslash = path.lastIndexOf('\\');
        source.setName(path.substring(Math.max(slash, backslash) + 1));
        return source;
    }
}
