package dev.qilletni.intellij.spotify.auth;

import com.intellij.ide.BrowserUtil;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.qilletni.intellij.QilletniBundle;
import dev.qilletni.intellij.packages.QilletniAccountsListener;
import dev.qilletni.intellij.packages.QilletniNotifications;
import dev.qilletni.intellij.settings.QilletniPersistence;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Application-level service managing Spotify OAuth (Authorization Code with PKCE) for the IDE's music search.
 * <p>
 * The Client ID and redirect URI are shared with the Qilletni Spotify package: they live in
 * {@code ~/.qilletni/persistence/spotify.properties} (what {@code qilletni persist spotify ...} writes), so one
 * Spotify app registration and redirect URI serve both the IDE and running programs. The IDE's refresh token is
 * stored in the IDE password safe. Access tokens are cached in memory until expiry.
 */
@Service
public final class SpotifyAuthService {
    private static final Logger LOG = Logger.getInstance(SpotifyAuthService.class);
    public static final String PACKAGE = "spotify";
    public static final String DEFAULT_REDIRECT_URI = "http://127.0.0.1:8088/spotify";

    // Settings used by earlier plugin versions; read once for migration
    private static final String LEGACY_CLIENT_ID = "qilletni.spotify.clientId";
    private static final String LEGACY_PORT = "qilletni.spotify.port";
    private static final String LEGACY_REFRESH = "qilletni.spotify.refreshToken";
    private static final CredentialAttributes REFRESH_TOKEN = new CredentialAttributes(
            CredentialAttributesKt.generateServiceName("Qilletni", "Spotify refresh token"));

    private final ExecutorService exec = AppExecutorUtil.createBoundedApplicationPoolExecutor("Qilletni Spotify", 4);
    private final java.util.concurrent.ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();
    private volatile @Nullable String cachedRefreshToken;
    private volatile boolean refreshTokenLoaded;

    private volatile @Nullable String accessToken;
    private volatile long accessTokenExpiryEpochSeconds = 0L;

    // Sign-in session tracking
    private volatile @Nullable com.sun.net.httpserver.HttpServer currentServer;
    private volatile @Nullable java.util.concurrent.CompletableFuture<Void> currentSignIn;
    private volatile @Nullable java.util.concurrent.ScheduledFuture<?> timeoutFuture;

    public static SpotifyAuthService getInstance() {
        return ApplicationManager.getApplication().getService(SpotifyAuthService.class);
    }

    /** The Spotify Client ID shared with the Qilletni Spotify package. */
    public @Nullable String getClientId() {
        var id = QilletniPersistence.get(PACKAGE, "clientId");
        return id != null ? id : normalize(PropertiesComponent.getInstance().getValue(LEGACY_CLIENT_ID));
    }

    /** The redirect URI shared with the Qilletni Spotify package; it must be registered in the Spotify app. */
    public @NotNull String getRedirectUri() {
        var uri = QilletniPersistence.get(PACKAGE, "redirectUri");
        if (uri != null) return uri;
        var legacyPort = PropertiesComponent.getInstance().getValue(LEGACY_PORT);
        return legacyPort != null ? "http://127.0.0.1:" + legacyPort + "/callback" : DEFAULT_REDIRECT_URI;
    }

    /** Saves the Client ID and redirect URI where both the IDE and Qilletni programs read them. */
    public void saveAppSettings(@Nullable String clientId, @Nullable String redirectUri) throws IOException {
        QilletniPersistence.update(PACKAGE, Map.of(
                "clientId", Objects.requireNonNullElse(normalize(clientId), ""),
                "redirectUri", Objects.requireNonNullElse(normalize(redirectUri), "")));
        PropertiesComponent.getInstance().unsetValue(LEGACY_CLIENT_ID);
        PropertiesComponent.getInstance().unsetValue(LEGACY_PORT);
    }

    private @Nullable String getRefreshToken() {
        if (!refreshTokenLoaded) {
            var stored = PasswordSafe.getInstance().getPassword(REFRESH_TOKEN);
            var legacy = normalize(PropertiesComponent.getInstance().getValue(LEGACY_REFRESH));
            if (stored == null && legacy != null) {
                PasswordSafe.getInstance().setPassword(REFRESH_TOKEN, legacy);
                stored = legacy;
            }
            PropertiesComponent.getInstance().unsetValue(LEGACY_REFRESH);
            cachedRefreshToken = normalize(stored);
            refreshTokenLoaded = true;
        }
        return cachedRefreshToken;
    }

    private void saveRefreshToken(@Nullable String rt) {
        cachedRefreshToken = normalize(rt);
        refreshTokenLoaded = true;
        PasswordSafe.getInstance().setPassword(REFRESH_TOKEN, cachedRefreshToken);
        QilletniAccountsListener.fire();
    }

    public boolean isSignedIn() { return getRefreshToken() != null; }

    public synchronized boolean isSignInInProgress() {
        return currentSignIn != null && !currentSignIn.isDone();
    }

    public synchronized void cancelSignIn() {
        if (timeoutFuture != null) { timeoutFuture.cancel(true); timeoutFuture = null; }
        if (currentServer != null) {
            try { currentServer.stop(0); } catch (Exception ignored) {}
            currentServer = null;
        }
        if (currentSignIn != null && !currentSignIn.isDone()) {
            currentSignIn.completeExceptionally(new java.util.concurrent.CancellationException("User canceled sign-in"));
        }
        currentSignIn = null;
    }

    public void signOut() {
        saveRefreshToken(null);
        accessToken = null;
        accessTokenExpiryEpochSeconds = 0L;
        cancelSignIn();
    }

    /**
     * Signs in with the system browser, showing progress with a cancel button and a notification with the outcome.
     *
     * @param onFinished called on the EDT when the flow ends, successfully or not
     */
    public void signInWithProgress(@Nullable Project project, @Nullable Runnable onFinished) {
        var flow = signIn();
        new Task.Backgroundable(project, QilletniBundle.message("spotify.signIn.progress"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText2(QilletniBundle.message("spotify.signIn.progress.details"));
                while (!flow.isDone()) {
                    if (indicator.isCanceled()) {
                        cancelSignIn();
                        break;
                    }
                    try {
                        flow.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException ignored) {
                        // keep waiting
                    } catch (Exception e) {
                        break;
                    }
                }
            }

            @Override
            public void onFinished() {
                if (flow.isCompletedExceptionally() && !flow.isCancelled()) {
                    var cause = flow.exceptionNow();
                    if (!(cause instanceof java.util.concurrent.CancellationException)) {
                        QilletniNotifications.group()
                                .createNotification(QilletniBundle.message("spotify.signIn.failed", cause.getMessage()), NotificationType.ERROR)
                                .addAction(QilletniNotifications.openSettingsAction(project != null ? project : ProjectManager.getInstance().getDefaultProject()))
                                .notify(project);
                    }
                } else if (flow.isDone() && !flow.isCompletedExceptionally()) {
                    QilletniNotifications.group()
                            .createNotification(QilletniBundle.message("spotify.signIn.success"), NotificationType.INFORMATION)
                            .notify(project);
                }
                if (onFinished != null) onFinished.run();
            }
        }.queue();
    }

    public CompletableFuture<Void> signIn() {
        var clientId = getClientId();
        if (clientId == null) return CompletableFuture.failedFuture(new IllegalStateException(QilletniBundle.message("spotify.signIn.noClientId")));
        var redirectUri = getRedirectUri();
        URI redirect;
        try {
            redirect = URI.create(redirectUri);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new IllegalStateException(QilletniBundle.message("spotify.signIn.badRedirect", redirectUri)));
        }
        int port = redirect.getPort() > 0 ? redirect.getPort() : 80;
        var callbackPath = redirect.getPath() == null || redirect.getPath().isEmpty() ? "/" : redirect.getPath();

        var verifier = randomUrlSafe(64);
        var challenge = base64UrlNoPad(sha256(verifier));
        var state = randomUrlSafe(24);

        // If a previous attempt is still in progress, cancel it so we can start fresh
        cancelSignIn();

        var result = new CompletableFuture<Void>();
        currentSignIn = result;
        exec.submit(() -> {
            com.sun.net.httpserver.HttpServer server = null;
            try {
                server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(redirect.getHost(), port), 0);
                currentServer = server;
            } catch (IOException e) {
                result.completeExceptionally(new IOException(QilletniBundle.message("spotify.signIn.portInUse", port, e.getMessage()), e));
                currentSignIn = null;
                return;
            }
            try {
                var authUrl = buildAuthUrl(clientId, redirectUri, state, challenge);
                BrowserUtil.browse(URI.create(authUrl));

                var finalServer = server;
                server.createContext(callbackPath, exchange -> {
                    try {
                        var query = exchange.getRequestURI().getRawQuery();
                        Map<String, String> params = splitQuery(query);
                        var gotState = params.get("state");
                        if (!Objects.equals(gotState, state)) {
                            respond(exchange, 400, "State mismatch");
                            result.completeExceptionally(new IllegalStateException("State mismatch"));
                            return;
                        }
                        var code = params.get("code");
                        if (code == null) {
                            respond(exchange, 400, "Missing code");
                            result.completeExceptionally(new IllegalStateException("Missing code"));
                            return;
                        }
                        var token = exchangeCodeForTokens(clientId, redirectUri, code, verifier);
                        if (token == null) {
                            respond(exchange, 500, "Token exchange failed");
                            result.completeExceptionally(new IllegalStateException("Token exchange failed"));
                            return;
                        }
                        accessToken = token.accessToken();
                        accessTokenExpiryEpochSeconds = Instant.now().getEpochSecond() + token.expiresIn();
                        if (token.refreshToken() != null && !token.refreshToken().isBlank()) saveRefreshToken(token.refreshToken());
                        respond(exchange, 200, "Signed in to Spotify for Qilletni. You can close this tab and return to the IDE.");
                        result.complete(null);
                    } catch (Exception ex) {
                        LOG.warn("Spotify callback error", ex);
                        try { respond(exchange, 500, "Internal error"); } catch (Exception ignored) {}
                        result.completeExceptionally(ex);
                    } finally {
                        try { finalServer.removeContext(callbackPath); } catch (Exception ignored) {}
                        try { finalServer.stop(0); } catch (Exception ignored) {}
                        currentServer = null;
                        if (timeoutFuture != null) { timeoutFuture.cancel(true); timeoutFuture = null; }
                        // Don't null currentSignIn here; leave it to completion handlers below to keep state consistent
                    }
                });
                server.setExecutor(exec);
                server.start();

                // Auto-timeout in case user closes the browser tab or never completes
                timeoutFuture = scheduler.schedule(() -> {
                    LOG.info("Spotify sign-in timed out; canceling in-progress flow");
                    if (!result.isDone()) result.completeExceptionally(new IllegalStateException(QilletniBundle.message("spotify.signIn.timeout")));
                    cancelSignIn();
                }, 120, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ex) {
                if (server != null) try { server.stop(0); } catch (Exception ignored) {}
                currentServer = null;
                if (timeoutFuture != null) { timeoutFuture.cancel(true); timeoutFuture = null; }
                result.completeExceptionally(ex);
            }
        });
        // Normalize clearing of currentSignIn when completed
        result.whenComplete((ok, ex) -> {
            if (timeoutFuture != null) { timeoutFuture.cancel(true); timeoutFuture = null; }
            currentSignIn = null;
        });
        return result;
    }

    public CompletableFuture<@NotNull String> getFreshAccessToken() {
        return CompletableFuture.supplyAsync(() -> {
            long now = Instant.now().getEpochSecond();
            if (accessToken != null && now < accessTokenExpiryEpochSeconds - 30) return accessToken;
            var clientId = getClientId();
            var refresh = getRefreshToken();
            if (clientId == null || refresh == null) throw new IllegalStateException("Not signed in to Spotify");
            var tr = refreshTokens(clientId, refresh);
            if (tr == null) throw new IllegalStateException("Failed to refresh Spotify token");
            accessToken = tr.accessToken();
            accessTokenExpiryEpochSeconds = now + tr.expiresIn();
            if (tr.refreshToken() != null && !tr.refreshToken().isBlank()) saveRefreshToken(tr.refreshToken());
            return accessToken;
        }, exec);
    }

    private record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}

    private @Nullable TokenResponse exchangeCodeForTokens(String clientId, String redirectUri, String code, String codeVerifier) {
        try {
            var form = "grant_type=authorization_code" +
                    "&code=" + url(code) +
                    "&redirect_uri=" + url(redirectUri) +
                    "&client_id=" + url(clientId) +
                    "&code_verifier=" + url(codeVerifier);
            return tokenRequest(form);
        } catch (Exception e) {
            LOG.warn("Token exchange failed", e);
            return null;
        }
        }

    private @Nullable TokenResponse refreshTokens(String clientId, String refreshToken) {
        try {
            var form = "grant_type=refresh_token" +
                    "&refresh_token=" + url(refreshToken) +
                    "&client_id=" + url(clientId);
            return tokenRequest(form);
        } catch (Exception e) {
            LOG.warn("Token refresh failed", e);
            return null;
        }
    }

    private @Nullable TokenResponse tokenRequest(String form) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder()
                .uri(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var client = HttpClient.newHttpClient();
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            LOG.warn("Spotify token endpoint status " + resp.statusCode() + ": " + resp.body());
            return null;
        }
        var json = resp.body();
        String access = extract(json, "\"access_token\"\s*:\s*\"", "\"");
        String refresh = extract(json, "\"refresh_token\"\s*:\s*\"", "\"");
        String expiresStr = extract(json, "\"expires_in\"\s*:\s*", ",");
        long expiresIn = 3600L;
        try { if (expiresStr != null) expiresIn = Long.parseLong(expiresStr.trim()); } catch (Exception ignored) {}
        if (access == null) return null;
        return new TokenResponse(access, refresh, expiresIn);
    }

    private static String buildAuthUrl(String clientId, String redirectUri, String state, String codeChallenge) {
        String scope = "playlist-read-private"; // adjust as needed later
        return "https://accounts.spotify.com/authorize?response_type=code" +
                "&client_id=" + url(clientId) +
                "&redirect_uri=" + url(redirectUri) +
                "&code_challenge_method=S256" +
                "&code_challenge=" + url(codeChallenge) +
                "&state=" + url(state) +
                "&scope=" + url(scope);
    }

    // --- utils ---
    private static String url(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String extract(String json, String prefixRegex, String endDelim) {
        var p = java.util.regex.Pattern.compile(prefixRegex);
        var m = p.matcher(json);
        if (m.find()) {
            int start = m.end();
            if (",".equals(endDelim)) {
                int end = json.indexOf(',', start);
                if (end < 0) end = json.indexOf('}', start);
                return end > start ? json.substring(start, end) : null;
            } else {
                int end = json.indexOf(endDelim, start);
                return end > start ? json.substring(start, end) : null;
            }
        }
        return null;
    }

    private static Map<String, String> splitQuery(@Nullable String query) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (var part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                var k = java.net.URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
                var v = java.net.URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String text) throws IOException {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static String randomUrlSafe(int bytes) {
        var rnd = new SecureRandom();
        var buf = new byte[bytes];
        rnd.nextBytes(buf);
        return base64UrlNoPad(buf);
    }

    private static byte[] sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String base64UrlNoPad(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    private static @Nullable String normalize(@Nullable String s) {
        if (s == null) return null;
        var t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
