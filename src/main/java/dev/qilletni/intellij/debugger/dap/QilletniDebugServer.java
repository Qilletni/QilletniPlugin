package dev.qilletni.intellij.debugger.dap;

import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

import java.util.concurrent.CompletableFuture;

/**
 * The DAP server, plus the Qilletni-specific requests in {@code docs/debugger-protocol.md}.
 */
public interface QilletniDebugServer extends IDebugProtocolServer {

    /** The songs a list, collection or album weight entry picks from. Older toolchains reject it. */
    @JsonRequest("qilletni/weightEntrySongs")
    CompletableFuture<WeightEntrySongsResponse> weightEntrySongs(WeightEntrySongsArguments args);
}
