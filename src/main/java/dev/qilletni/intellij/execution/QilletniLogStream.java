package dev.qilletni.intellij.execution;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Receives the toolchain's structured log4j events. The toolchain is started with {@code --log-port <port>} and
 * connects back to this socket, sending one JSON object per line.
 */
public final class QilletniLogStream {
    private static final Logger LOG = Logger.getInstance(QilletniLogStream.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** One toolchain log event. {@code stackTrace} is the formatted exception, if any. */
    public record LogRecord(@NotNull Level level, @NotNull String time, @NotNull String thread,
                            @NotNull String message, @Nullable String stackTrace) {
        /** The record formatted as a single console line, without stack trace. */
        public @NotNull String formatLine() {
            return "[" + time + "] [" + thread + "/" + level + "]: " + message;
        }
    }

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR;

        static Level parse(String level) {
            return switch (level == null ? "" : level) {
                case "TRACE" -> TRACE;
                case "DEBUG" -> DEBUG;
                case "WARN" -> WARN;
                case "ERROR", "FATAL" -> ERROR;
                default -> INFO;
            };
        }
    }

    private final ServerSocket server;

    public QilletniLogStream() throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    public int getPort() {
        return server.getLocalPort();
    }

    /**
     * Starts delivering records for the given process. The socket is closed when the process ends; it is the caller's
     * job to call {@link #close()} if the process never starts.
     */
    public void attach(@NotNull ProcessHandler handler, @NotNull Consumer<LogRecord> consumer) {
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                // Give the reader a moment to drain buffered events before the socket goes away
                AppExecutorUtil.getAppScheduledExecutorService().schedule(QilletniLogStream.this::close, 2, java.util.concurrent.TimeUnit.SECONDS);
            }
        });
        AppExecutorUtil.getAppExecutorService().execute(() -> pump(consumer));
    }

    private void pump(Consumer<LogRecord> consumer) {
        try (var socket = server.accept();
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                consumer.accept(parse(line));
            }
        } catch (IOException e) {
            // Socket closed: the process ended without connecting, or finished
        } finally {
            close();
        }
    }

    public void close() {
        try {
            server.close();
        } catch (IOException e) {
            LOG.debug(e);
        }
    }

    static @NotNull LogRecord parse(@NotNull String line) {
        JsonObject o;
        try {
            o = JsonParser.parseString(line).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return new LogRecord(Level.INFO, TIME.format(Instant.now().atZone(ZoneId.systemDefault())), "?", line, null);
        }
        return new LogRecord(
                Level.parse(string(o.get("level"))),
                formatTime(o),
                o.has("thread") ? string(o.get("thread")) : "?",
                string(o.get("message")),
                formatThrown(o));
    }

    private static @Nullable String formatThrown(JsonObject o) {
        if (!(o.get("thrown") instanceof JsonObject thrown)) return null;
        var sb = new StringBuilder(thrown.has("name") ? string(thrown.get("name")) : "Exception");
        var message = thrown.has("localizedMessage") ? string(thrown.get("localizedMessage")) : string(thrown.get("message"));
        if (!message.isEmpty()) sb.append(": ").append(message);
        if (thrown.has("extendedStackTrace") && thrown.get("extendedStackTrace").isJsonArray()) {
            for (JsonElement el : thrown.getAsJsonArray("extendedStackTrace")) {
                if (!(el instanceof JsonObject frame)) continue;
                sb.append("\n\tat ").append(string(frame.get("class"))).append('.').append(string(frame.get("method")))
                        .append('(').append(frame.has("file") ? string(frame.get("file")) : "Unknown Source");
                if (frame.has("line") && frame.get("line").getAsInt() >= 0) sb.append(':').append(frame.get("line").getAsInt());
                sb.append(')');
            }
        }
        return sb.toString();
    }

    private static String formatTime(JsonObject o) {
        Instant instant;
        if (o.get("instant") instanceof JsonObject inst && inst.has("epochSecond")) {
            instant = Instant.ofEpochSecond(inst.get("epochSecond").getAsLong(), inst.has("nanoOfSecond") ? inst.get("nanoOfSecond").getAsInt() : 0);
        } else if (o.has("timeMillis")) {
            instant = Instant.ofEpochMilli(o.get("timeMillis").getAsLong());
        } else {
            instant = Instant.now();
        }
        return TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    private static String string(@Nullable JsonElement e) {
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
