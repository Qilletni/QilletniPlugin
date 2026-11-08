package dev.qilletni.intellij.debugger.dap;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.ExitedEventArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArgumentsPathFormat;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The IDE end of a Debug Adapter Protocol connection to the toolchain. The IDE listens on a loopback port and the
 * toolchain, started with {@code --debug-port <port>}, connects to it and acts as the debug adapter.
 * <p>
 * This class owns the socket, the startup sequence and event delivery. It has no IntelliJ debugger dependencies, so
 * it can be driven by tests against a fake adapter or a real toolchain. Events reach the {@link Listener} on a single
 * dedicated thread, in order, never on lsp4j's reader thread; a listener may therefore block on further requests.
 */
public final class QilletniDapSession implements AutoCloseable {
    /** The contract version sent in {@code launch}; the adapter rejects versions it doesn't know. */
    public static final int PROTOCOL_VERSION = 1;
    /** Qilletni programs run on one thread. */
    public static final int THREAD_ID = 1;

    /** Receives adapter events. Called on the session's event thread. */
    public interface Listener {
        void stopped(@NotNull StoppedEventArguments args);

        default void breakpointChanged(@NotNull Breakpoint breakpoint) {}

        default void output(@NotNull OutputEventArguments args) {}

        default void terminated() {}

        default void exited(int exitCode) {}

        /** The connection closed, whether or not the program finished cleanly. */
        default void disconnected() {}

        /** The adapter rejected {@code launch}, for example because it doesn't support {@link #PROTOCOL_VERSION}. */
        default void launchFailed(@NotNull String message) {}

        /** A weighted pick was made. */
        default void weightPicked(@NotNull WeightPickedEventArguments args) {}
    }

    /** Thrown when the toolchain doesn't connect: it exited first (old toolchain) or timed out. */
    public static final class ConnectException extends IOException {
        private final boolean processExited;

        ConnectException(String message, boolean processExited) {
            super(message);
            this.processExited = processExited;
        }

        public boolean isProcessExited() {
            return processExited;
        }
    }

    private final ServerSocket serverSocket;
    private final ExecutorService events = Executors.newSingleThreadExecutor(daemon("Qilletni debugger events"));
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    private volatile @Nullable Socket socket;
    private volatile @Nullable QilletniDebugServer server;
    private volatile @NotNull Capabilities capabilities = new Capabilities();
    private volatile @Nullable Listener listener;
    private volatile boolean closed;

    public QilletniDapSession() throws IOException {
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** The adapter's proxy. Only valid once {@link #connect} has completed. */
    public @NotNull QilletniDebugServer server() {
        var s = server;
        if (s == null) throw new IllegalStateException("Debug adapter not connected");
        return s;
    }

    public @NotNull Capabilities capabilities() {
        return capabilities;
    }

    public boolean isConnected() {
        return server != null && !closed;
    }

    /**
     * Waits for the toolchain to connect, then sends {@code initialize}. Completes with the adapter's capabilities.
     * Fails with {@link ConnectException} if {@code processAlive} turns false or {@code timeout} passes first.
     */
    public @NotNull CompletableFuture<Capabilities> connect(@NotNull Listener listener, @NotNull BooleanSupplier processAlive,
                                                           @NotNull Duration timeout) {
        this.listener = listener;
        var accepted = new CompletableFuture<Socket>();
        daemon("Qilletni debugger connect").newThread(() -> {
            try {
                accepted.complete(accept(processAlive, timeout));
            } catch (IOException e) {
                accepted.completeExceptionally(e);
            }
        }).start();
        return accepted.thenCompose(s -> {
            socket = s;
            try {
                var launcher = DebugLauncher.createLauncher(new Client(), QilletniDebugServer.class, s.getInputStream(), s.getOutputStream());
                server = launcher.getRemoteProxy();
                var listening = launcher.startListening();
                daemon("Qilletni debugger reader").newThread(() -> {
                    try {
                        listening.get();
                    } catch (Exception ignored) {
                        // Stream closed
                    }
                    deliver(Listener::disconnected);
                }).start();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
            return server().initialize(initializeArguments());
        }).thenApply(caps -> {
            capabilities = caps != null ? caps : new Capabilities();
            return capabilities;
        });
    }

    private Socket accept(BooleanSupplier processAlive, Duration timeout) throws IOException {
        var deadline = System.nanoTime() + timeout.toNanos();
        serverSocket.setSoTimeout(250);
        while (true) {
            try {
                return serverSocket.accept();
            } catch (SocketTimeoutException e) {
                if (closed) throw new ConnectException("Debug session closed before the toolchain connected", false);
                if (!processAlive.getAsBoolean()) throw new ConnectException("The toolchain exited before connecting to the debugger", true);
                if (System.nanoTime() > deadline) throw new ConnectException("The toolchain did not connect to the debugger in time", false);
            }
        }
    }

    static @NotNull InitializeRequestArguments initializeArguments() {
        var args = new InitializeRequestArguments();
        args.setClientID("intellij-qilletni");
        args.setClientName("IntelliJ Qilletni");
        args.setAdapterID("qilletni");
        args.setLocale("en");
        args.setLinesStartAt1(true);
        args.setColumnsStartAt1(true);
        args.setPathFormat(InitializeRequestArgumentsPathFormat.PATH);
        args.setSupportsVariableType(true);
        args.setSupportsVariablePaging(true);
        return args;
    }

    /**
     * Runs the configuration phase: {@code launch}, then (after the adapter's {@code initialized} event) whatever
     * {@code sendConfiguration} sends, typically breakpoints, then {@code configurationDone}. The program starts
     * running when the returned future completes.
     * <p>
     * The {@code launch} response is not awaited before configuring, since adapters may answer it late; a rejection
     * is reported through {@link Listener#launchFailed}.
     */
    public @NotNull CompletableFuture<Void> configure(boolean stopOnEntry, @NotNull Supplier<CompletableFuture<?>> sendConfiguration) {
        server().launch(Map.of("qilletniProtocol", PROTOCOL_VERSION, "stopOnEntry", stopOnEntry, "weightEvents", true))
                .exceptionally(e -> {
                    deliver(l -> l.launchFailed(messageOf(e)));
                    return null;
                });
        return initialized
                .thenCompose(v -> sendConfiguration.get())
                .thenCompose(v -> server().configurationDone(new ConfigurationDoneArguments()));
    }

    /** Asks the adapter to end the program, then closes the connection. Safe to call more than once. */
    @Override
    public void close() {
        if (closed) return;
        var s = server;
        if (s != null) {
            var args = new DisconnectArguments();
            args.setTerminateDebuggee(true);
            try {
                s.disconnect(args).get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // The adapter may already be gone
            }
        }
        closed = true;
        closeQuietly(socket);
        closeQuietly(serverSocket);
        events.shutdown();
    }

    /** A readable message for a failed request, without the wrapping exceptions. */
    public static @NotNull String messageOf(@NotNull Throwable e) {
        var cause = e;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof org.eclipse.lsp4j.jsonrpc.ResponseErrorException rex && rex.getResponseError() != null) {
            return rex.getResponseError().getMessage();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    private void deliver(java.util.function.Consumer<Listener> event) {
        var l = listener;
        if (l == null || events.isShutdown()) return;
        try {
            events.execute(() -> event.accept(l));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Closed meanwhile
        }
    }

    private final class Client implements QilletniDebugClient {
        @Override
        public void initialized() {
            initialized.complete(null);
        }

        @Override
        public void stopped(StoppedEventArguments args) {
            deliver(l -> l.stopped(args));
        }

        @Override
        public void breakpoint(BreakpointEventArguments args) {
            if (args.getBreakpoint() != null) deliver(l -> l.breakpointChanged(args.getBreakpoint()));
        }

        @Override
        public void output(OutputEventArguments args) {
            deliver(l -> l.output(args));
        }

        @Override
        public void terminated(TerminatedEventArguments args) {
            deliver(Listener::terminated);
        }

        @Override
        public void exited(ExitedEventArguments args) {
            deliver(l -> l.exited(args.getExitCode()));
        }

        @Override
        public void weightPicked(WeightPickedEventArguments args) {
            if (args != null) deliver(l -> l.weightPicked(args));
        }
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            var t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private static void closeQuietly(@Nullable AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
            // Already closed
        }
    }
}
