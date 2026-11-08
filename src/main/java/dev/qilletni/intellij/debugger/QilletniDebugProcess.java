package dev.qilletni.intellij.debugger;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.debugger.dap.QilletniDapSession;
import dev.qilletni.intellij.debugger.dap.WeightEntrySongsArguments;
import dev.qilletni.intellij.debugger.dap.WeightPickedEventArguments;
import dev.qilletni.intellij.debugger.frames.QilletniStackFrame;
import dev.qilletni.intellij.debugger.frames.QilletniSuspendContext;
import dev.qilletni.intellij.debugger.weights.QilletniWeightStats;
import dev.qilletni.intellij.debugger.weights.QilletniWeightsPanel;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A debug session of one {@code qilletni run}. The toolchain is the debug adapter; this class translates between
 * IntelliJ's debugger and DAP. See {@code docs/debugger-protocol.md} for the contract the toolchain implements.
 */
public final class QilletniDebugProcess extends XDebugProcess implements QilletniDapSession.Listener {
    private static final Logger LOG = Logger.getInstance(QilletniDebugProcess.class);
    /** Generous: the toolchain's JVM starts and loads libraries before it connects. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(60);
    private static final long STACK_TIMEOUT_SECONDS = 30;
    private static final String WEIGHTS_CONTENT_ID = "QilletniWeights";

    private final QilletniDapSession dap;
    private final ExecutionResult executionResult;
    private final QilletniDebugSourceMapper mapper;
    private final QilletniLineBreakpointHandler lineBreakpoints = new QilletniLineBreakpointHandler(this);
    private final List<QilletniFilterBreakpointHandler> filterBreakpoints = List.of(
            new QilletniFilterBreakpointHandler(this, QilletniExceptionBreakpointType.class),
            new QilletniFilterBreakpointHandler(this, QilletniSongPlayedBreakpointType.class),
            new QilletniFilterBreakpointHandler(this, QilletniWeightSelectedBreakpointType.class));
    private final QilletniDebuggerEditorsProvider editorsProvider = new QilletniDebuggerEditorsProvider();
    private final QilletniWeightStats weightStats = new QilletniWeightStats();
    private volatile @Nullable QilletniWeightsPanel weightsPanel;
    private volatile boolean configured;

    QilletniDebugProcess(@NotNull XDebugSession session, @NotNull QilletniDapSession dap,
                         @NotNull ExecutionResult executionResult, @NotNull QilletniDebugSourceMapper mapper) {
        super(session);
        this.dap = dap;
        this.executionResult = executionResult;
        this.mapper = mapper;
    }

    public @NotNull QilletniDapSession dap() {
        return dap;
    }

    public @NotNull QilletniDebugSourceMapper mapper() {
        return mapper;
    }

    /** Whether the configuration phase has started, after which breakpoint changes go to the adapter immediately. */
    boolean isConfigured() {
        return configured;
    }

    /**
     * Sends every enabled filter breakpoint the adapter advertises, in one request: each request replaces the whole
     * set, so sending filters one at a time would turn the others off.
     */
    synchronized @NotNull CompletableFuture<Void> sendFilterBreakpoints() {
        var advertised = dap.capabilities().getExceptionBreakpointFilters();
        if (advertised == null || advertised.length == 0) return CompletableFuture.completedFuture(null);
        var filters = filterBreakpoints.stream()
                .filter(handler -> handler.breakpoint() != null)
                .map(handler -> handler.type().filter())
                .filter(filter -> Arrays.stream(advertised).anyMatch(f -> filter.equals(f.getFilter())))
                .toArray(String[]::new);
        var args = new SetExceptionBreakpointsArguments();
        args.setFilters(filters);
        return dap.server().setExceptionBreakpoints(args).handle((r, e) -> null);
    }

    public boolean supportsHoverEvaluation() {
        return Boolean.TRUE.equals(dap.capabilities().getSupportsEvaluateForHovers());
    }

    public boolean supportsSetVariable() {
        return Boolean.TRUE.equals(dap.capabilities().getSupportsSetVariable());
    }

    @Override
    public void sessionInitialized() {
        var handler = executionResult.getProcessHandler();
        if (!handler.isStartNotified()) handler.startNotify();
        dap.connect(this, () -> !handler.isProcessTerminated() && !handler.isProcessTerminating(), CONNECT_TIMEOUT)
                .thenCompose(capabilities -> dap.configure(false, () -> {
                    configured = true;
                    return CompletableFuture.allOf(lineBreakpoints.sendAll(), sendFilterBreakpoints());
                }))
                .exceptionally(error -> {
                    failToStart(error);
                    return null;
                });
    }

    private void failToStart(Throwable error) {
        var cause = error;
        while (cause.getCause() != null && !(cause instanceof QilletniDapSession.ConnectException)) cause = cause.getCause();
        String message;
        if (cause instanceof QilletniDapSession.ConnectException ce && ce.isProcessExited()) {
            message = QilletniBundle.message("debugger.error.toolchainExited");
        } else {
            message = QilletniBundle.message("debugger.error.start", QilletniDapSession.messageOf(error));
        }
        LOG.info("Qilletni debug session failed to start", error);
        getSession().reportError(message);
        destroyProcess();
    }

    private void destroyProcess() {
        var handler = executionResult.getProcessHandler();
        if (!handler.isProcessTerminated()) handler.destroyProcess();
    }

    // Adapter events, delivered on the session's event thread

    @Override
    public void stopped(@NotNull StoppedEventArguments args) {
        StackFrame[] frames;
        try {
            var stackArgs = new StackTraceArguments();
            stackArgs.setThreadId(args.getThreadId() != null ? args.getThreadId() : QilletniDapSession.THREAD_ID);
            var response = dap.server().stackTrace(stackArgs).get(STACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            frames = response != null && response.getStackFrames() != null ? response.getStackFrames() : new StackFrame[0];
        } catch (Exception e) {
            LOG.warn("Could not read the stack after a stop", e);
            frames = new StackFrame[0];
        }
        var context = new QilletniSuspendContext(this, Arrays.stream(frames).map(f -> new QilletniStackFrame(this, f)).toList());
        var session = getSession();

        if (StoppedEventArgumentsReason.BREAKPOINT.equals(args.getReason())) {
            var breakpoint = lineBreakpoints.findHit(args.getHitBreakpointIds());
            if (breakpoint != null) {
                reachBreakpoint(breakpoint, frames, context);
                return;
            }
        } else {
            if (StoppedEventArgumentsReason.EXCEPTION.equals(args.getReason())) {
                var text = args.getText() != null ? args.getText() : args.getDescription();
                if (text != null) session.getConsoleView().print(text + "\n", ConsoleViewContentType.ERROR_OUTPUT);
            } else if (QilletniSongPlayedBreakpointType.STOP_REASON.equals(args.getReason())
                    || QilletniWeightSelectedBreakpointType.STOP_REASON.equals(args.getReason())) {
                if (args.getDescription() != null) session.getConsoleView().print(args.getDescription() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
            }
            var breakpoint = filterBreakpoints.stream()
                    .filter(handler -> handler.type().stopReason().equals(args.getReason()))
                    .map(QilletniFilterBreakpointHandler::breakpoint)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (breakpoint != null) {
                reachBreakpoint(breakpoint, frames, context);
                return;
            }
        }
        session.positionReached(context);
    }

    /**
     * The platform leaves conditions and log expressions to the debug process: {@code breakpointReached} only prints
     * an already-evaluated log message and applies the suspend policy. Both are evaluated here, in the top frame,
     * through the adapter's {@code evaluate}. A condition that fails to evaluate suspends, so the error is noticed.
     */
    private void reachBreakpoint(XBreakpoint<?> breakpoint, StackFrame[] frames, QilletniSuspendContext context) {
        var session = getSession();
        var condition = breakpoint.getConditionExpression();
        if (condition != null && !condition.getExpression().isBlank() && frames.length > 0) {
            try {
                var result = evaluateNow(frames[0].getId(), condition.getExpression());
                if (!"true".equals(result.getResult())) {
                    resume(context);
                    return;
                }
            } catch (Exception e) {
                session.getConsoleView().print(QilletniBundle.message("debugger.error.condition",
                        condition.getExpression(), QilletniDapSession.messageOf(e)) + "\n", ConsoleViewContentType.ERROR_OUTPUT);
            }
        }

        String logMessage = null;
        var log = breakpoint.getLogExpressionObject();
        if (log != null && !log.getExpression().isBlank() && frames.length > 0) {
            try {
                logMessage = evaluateNow(frames[0].getId(), log.getExpression()).getResult();
            } catch (Exception e) {
                logMessage = QilletniBundle.message("debugger.error.logExpression", log.getExpression(), QilletniDapSession.messageOf(e));
            }
        }

        // False when the suspend policy is "none", e.g. a logging-only breakpoint
        if (!session.breakpointReached(breakpoint, logMessage, context)) resume(context);
    }

    /** Evaluates synchronously; only called on the session's event thread, never on lsp4j's reader thread. */
    private @NotNull EvaluateResponse evaluateNow(int frameId, @NotNull String expression) throws Exception {
        var args = new EvaluateArguments();
        args.setFrameId(frameId);
        args.setExpression(expression);
        args.setContext(EvaluateArgumentsContext.WATCH);
        return dap.server().evaluate(args).get(STACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void breakpointChanged(@NotNull Breakpoint breakpoint) {
        lineBreakpoints.breakpointChanged(breakpoint);
    }

    @Override
    public void output(@NotNull OutputEventArguments args) {
        if (args.getOutput() == null || OutputEventArgumentsCategory.TELEMETRY.equals(args.getCategory())) return;
        var type = OutputEventArgumentsCategory.STDERR.equals(args.getCategory())
                ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT;
        getSession().getConsoleView().print(args.getOutput(), type);
    }

    @Override
    public void weightPicked(@NotNull WeightPickedEventArguments args) {
        weightStats.add(args);
        for (var request : weightStats.autoRequests(QilletniDebuggerSettings.getInstance().getWeightSongLimit())) {
            requestEntrySongs(request.weightsId(), request.key(), false);
        }
        refreshWeights();
    }

    /** Fetches every song of a Weights tab entry when the user asks for them. */
    private void loadEntrySongs(int weightsId, @NotNull String key) {
        if (weightStats.markRequested(weightsId, key)) requestEntrySongs(weightsId, key, true);
    }

    /**
     * @param retryOnFailure If a failed request may be made again; automatic ones aren't, so an older toolchain isn't
     *                       asked on every pick
     */
    private void requestEntrySongs(int weightsId, @NotNull String key, boolean retryOnFailure) {
        var args = new WeightEntrySongsArguments();
        args.weightsId = weightsId;
        args.indices = QilletniWeightStats.indices(key);
        dap.server().weightEntrySongs(args).whenComplete((songs, error) -> {
            if (error != null || songs == null) {
                LOG.debug("Could not get the songs of weight entry " + key, error);
                if (retryOnFailure) weightStats.requestFailed(weightsId, key);
                return;
            }

            weightStats.setEntrySongs(weightsId, key, songs);
            refreshWeights();
        });
    }

    private void refreshWeights() {
        var panel = weightsPanel;
        if (panel != null) panel.refreshLater();
    }

    @Override
    public void launchFailed(@NotNull String message) {
        getSession().reportError(QilletniBundle.message("debugger.error.launch", message));
        destroyProcess();
    }

    // Requests used by frames, values and the evaluator

    public @NotNull CompletableFuture<ScopesResponse> scopes(int frameId) {
        var args = new ScopesArguments();
        args.setFrameId(frameId);
        return dap.server().scopes(args);
    }

    /** Children of {@code reference}; {@code filter}, {@code start} and {@code count} are only sent when non-null. */
    public @NotNull CompletableFuture<Variable[]> variables(int reference, @Nullable VariablesArgumentsFilter filter,
                                                          @Nullable Integer start, @Nullable Integer count) {
        var args = new VariablesArguments();
        args.setVariablesReference(reference);
        args.setFilter(filter);
        args.setStart(start);
        args.setCount(count);
        return dap.server().variables(args).thenApply(r -> r != null && r.getVariables() != null ? r.getVariables() : new Variable[0]);
    }

    // Execution control

    @Override
    public void resume(@Nullable XSuspendContext context) {
        var args = new ContinueArguments();
        args.setThreadId(QilletniDapSession.THREAD_ID);
        dap.server().continue_(args).exceptionally(this::logFailure);
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        var args = new NextArguments();
        args.setThreadId(QilletniDapSession.THREAD_ID);
        dap.server().next(args).exceptionally(this::logFailure);
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        var args = new StepInArguments();
        args.setThreadId(QilletniDapSession.THREAD_ID);
        dap.server().stepIn(args).exceptionally(this::logFailure);
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        var args = new StepOutArguments();
        args.setThreadId(QilletniDapSession.THREAD_ID);
        dap.server().stepOut(args).exceptionally(this::logFailure);
    }

    @Override
    public void startPausing() {
        var args = new PauseArguments();
        args.setThreadId(QilletniDapSession.THREAD_ID);
        dap.server().pause(args).exceptionally(this::logFailure);
    }

    @Override
    public void stop() {
        // Sends disconnect, which may wait briefly for the adapter
        AppExecutorUtil.getAppExecutorService().execute(dap::close);
    }

    private <T> T logFailure(Throwable error) {
        LOG.info("Debugger request failed: " + QilletniDapSession.messageOf(error));
        return null;
    }

    // Wiring

    @Override
    public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
        return editorsProvider;
    }

    @Override
    public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
        var handlers = new ArrayList<XBreakpointHandler<?>>();
        handlers.add(lineBreakpoints);
        handlers.addAll(filterBreakpoints);
        return handlers.toArray(XBreakpointHandler<?>[]::new);
    }

    @Override
    protected @Nullable ProcessHandler doGetProcessHandler() {
        return executionResult.getProcessHandler();
    }

    @Override
    public @NotNull ExecutionConsole createConsole() {
        return executionResult.getExecutionConsole();
    }

    /** Adds the Weights tab, which shows weighted picks as they happen. */
    @Override
    public @NotNull XDebugTabLayouter createTabLayouter() {
        return new XDebugTabLayouter() {
            @Override
            public void registerAdditionalContent(@NotNull RunnerLayoutUi ui) {
                var panel = new QilletniWeightsPanel(weightStats, QilletniDebugProcess.this::loadEntrySongs);
                var content = ui.createContent(WEIGHTS_CONTENT_ID, panel, QilletniBundle.message("debugger.weights.tab"), null, null);
                content.setCloseable(false);
                Disposer.register(content, panel);
                ui.addContent(content, 0, PlaceInGrid.center, false);
                weightsPanel = panel;
                panel.refreshLater();
            }
        };
    }

    /** Keep variables in the adapter's order: scopes and entity fields are already meaningfully ordered. */
    @Override
    public boolean isValuesCustomSorted() {
        return true;
    }
}
