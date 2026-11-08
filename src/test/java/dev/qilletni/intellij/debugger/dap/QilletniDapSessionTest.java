package dev.qilletni.intellij.debugger.dap;

import junit.framework.TestCase;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.ExitedEventArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class QilletniDapSessionTest extends TestCase {
    private QilletniDapSession session;
    private FakeQilletniAdapter adapter;
    private final RecordingListener listener = new RecordingListener();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        session = new QilletniDapSession();
        adapter = new FakeQilletniAdapter();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            session.close();
            adapter.close();
        } finally {
            super.tearDown();
        }
    }

    private void connect() throws Exception {
        var capabilities = session.connect(listener, () -> true, Duration.ofSeconds(10));
        adapter.connect(session.getPort());
        capabilities.get(10, TimeUnit.SECONDS);
    }

    public void testStartupSequence() throws Exception {
        connect();
        assertTrue(Boolean.TRUE.equals(session.capabilities().getSupportsConfigurationDoneRequest()));
        assertTrue(adapter.initializeArgs.getLinesStartAt1());
        assertTrue(adapter.initializeArgs.getColumnsStartAt1());

        session.configure(false, () -> {
            var args = new SetBreakpointsArguments();
            var source = new Source();
            source.setPath("/p/main.ql");
            args.setSource(source);
            var bp = new SourceBreakpoint();
            bp.setLine(3);
            args.setBreakpoints(new SourceBreakpoint[]{bp});
            return session.server().setBreakpoints(args);
        }).get(10, TimeUnit.SECONDS);

        assertEquals(List.of("initialize", "launch", "setBreakpoints", "configurationDone"), adapter.requests);
        assertEquals(1.0, ((Number) adapter.launchArgs.get("qilletniProtocol")).doubleValue());
        assertEquals(Boolean.FALSE, adapter.launchArgs.get("stopOnEntry"));
        assertEquals(3, adapter.lastBreakpoints.getBreakpoints()[0].getLine());
    }

    public void testEventsReachListenerInOrder() throws Exception {
        connect();
        var stopped = new StoppedEventArguments();
        stopped.setReason("breakpoint");
        stopped.setThreadId(QilletniDapSession.THREAD_ID);
        stopped.setHitBreakpointIds(new Integer[]{100});
        adapter.client.stopped(stopped);
        var changed = new BreakpointEventArguments();
        changed.setReason("changed");
        var bp = new Breakpoint();
        bp.setId(7);
        bp.setVerified(true);
        changed.setBreakpoint(bp);
        adapter.client.breakpoint(changed);
        adapter.client.terminated(new TerminatedEventArguments());
        var exited = new ExitedEventArguments();
        exited.setExitCode(3);
        adapter.client.exited(exited);

        assertEquals("stopped:breakpoint", listener.next());
        assertEquals("breakpoint:7", listener.next());
        assertEquals("terminated", listener.next());
        assertEquals("exited:3", listener.next());
    }

    public void testRejectedLaunchIsReported() throws Exception {
        adapter.rejectLaunch = true;
        connect();
        session.configure(false, () -> CompletableFuture.completedFuture(null)).get(10, TimeUnit.SECONDS);
        assertEquals("launchFailed:Unsupported qilletniProtocol", listener.next());
    }

    public void testCloseDisconnectsAndTerminates() throws Exception {
        connect();
        session.close();
        assertTrue(adapter.requests.contains("disconnect"));
        assertTrue(adapter.disconnectArgs.getTerminateDebuggee());
    }

    public void testProcessExitingBeforeConnectingFailsFast() throws Exception {
        var start = System.nanoTime();
        try {
            session.connect(listener, () -> false, Duration.ofSeconds(30)).get(10, TimeUnit.SECONDS);
            fail("Expected the connection to fail");
        } catch (ExecutionException e) {
            var cause = (QilletniDapSession.ConnectException) e.getCause();
            assertTrue(cause.isProcessExited());
        }
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
    }

    public void testMessageOfUnwrapsResponseErrors() {
        var error = new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(1, "No variable named x", null));
        assertEquals("No variable named x", QilletniDapSession.messageOf(new java.util.concurrent.CompletionException(error)));
    }

    private static final class RecordingListener implements QilletniDapSession.Listener {
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>();

        String next() throws InterruptedException {
            var event = events.poll(10, TimeUnit.SECONDS);
            assertNotNull("Timed out waiting for an event", event);
            return event;
        }

        @Override
        public void stopped(@NotNull StoppedEventArguments args) {
            events.add("stopped:" + args.getReason());
        }

        @Override
        public void breakpointChanged(@NotNull Breakpoint breakpoint) {
            events.add("breakpoint:" + breakpoint.getId());
        }

        @Override
        public void terminated() {
            events.add("terminated");
        }

        @Override
        public void exited(int exitCode) {
            events.add("exited:" + exitCode);
        }

        @Override
        public void disconnected() {
            events.add("disconnected");
        }

        @Override
        public void launchFailed(@NotNull String message) {
            events.add("launchFailed:" + message);
        }
    }
}
