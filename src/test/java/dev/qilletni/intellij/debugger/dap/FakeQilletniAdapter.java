package dev.qilletni.intellij.debugger.dap;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for the toolchain's debug adapter: it records requests and answers them the way the contract in
 * {@code docs/debugger-protocol.md} describes, so the IDE side can be tested without a toolchain.
 */
final class FakeQilletniAdapter implements IDebugProtocolServer {
    final List<String> requests = new CopyOnWriteArrayList<>();
    volatile InitializeRequestArguments initializeArgs;
    volatile Map<String, Object> launchArgs;
    volatile SetBreakpointsArguments lastBreakpoints;
    volatile DisconnectArguments disconnectArgs;
    volatile boolean rejectLaunch;
    volatile IDebugProtocolClient client;
    private Socket socket;

    void connect(int port) throws IOException {
        socket = new Socket(InetAddress.getLoopbackAddress(), port);
        var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream());
        client = launcher.getRemoteProxy();
        launcher.startListening();
    }

    void close() throws IOException {
        if (socket != null) socket.close();
    }

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        requests.add("initialize");
        initializeArgs = args;
        var caps = new Capabilities();
        caps.setSupportsConfigurationDoneRequest(true);
        caps.setSupportsEvaluateForHovers(true);
        // Send "initialized" after the response has gone out
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            client.initialized();
        });
        return CompletableFuture.completedFuture(caps);
    }

    @Override
    public CompletableFuture<Void> launch(Map<String, Object> args) {
        requests.add("launch");
        launchArgs = args;
        if (rejectLaunch) {
            return CompletableFuture.failedFuture(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported qilletniProtocol", null)));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        requests.add("setBreakpoints");
        lastBreakpoints = args;
        var response = new SetBreakpointsResponse();
        var results = new Breakpoint[args.getBreakpoints().length];
        for (int i = 0; i < results.length; i++) {
            var bp = new Breakpoint();
            bp.setId(100 + i);
            bp.setVerified(true);
            bp.setLine(args.getBreakpoints()[i].getLine());
            results[i] = bp;
        }
        response.setBreakpoints(results);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        requests.add("configurationDone");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        requests.add("disconnect");
        disconnectArgs = args;
        return CompletableFuture.completedFuture(null);
    }
}
