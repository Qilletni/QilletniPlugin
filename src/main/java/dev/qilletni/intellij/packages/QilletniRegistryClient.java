package dev.qilletni.intellij.packages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Read-only client for the Qilletni package registry, with short-lived caching for completion. */
@Service
public final class QilletniRegistryClient {
    private static final Logger LOG = Logger.getInstance(QilletniRegistryClient.class);
    private static final String BASE_URL = "https://qpm.qilletni.dev";
    private static final long TTL = TimeUnit.MINUTES.toMillis(10);

    /** @param id the package id, {@code scope/name}; @param latest its latest version, if known */
    public record RegistryPackage(@NotNull String id, @NotNull String latest) {}

    private record Cached<T>(T value, long fetchedAt) {
        boolean fresh() { return System.currentTimeMillis() - fetchedAt < TTL; }
    }

    private volatile Cached<List<RegistryPackage>> packages;
    private final Map<String, Cached<List<String>>> versions = new ConcurrentHashMap<>();

    public static QilletniRegistryClient getInstance() {
        return ApplicationManager.getApplication().getService(QilletniRegistryClient.class);
    }

    /** All published packages. Blocks on the network when the cache is stale; call from a background thread. */
    public @NotNull List<RegistryPackage> listPackages() {
        var cached = packages;
        if (cached != null && cached.fresh()) return cached.value();
        try {
            var json = JsonParser.parseString(HttpRequests.request(BASE_URL + "/packages")
                    .connectTimeout(3_000).readTimeout(5_000).readString()).getAsJsonObject();
            var result = new ArrayList<RegistryPackage>();
            for (var element : json.getAsJsonArray("packages")) {
                if (!(element instanceof JsonObject pkg) || !pkg.has("name")) continue;
                var latest = pkg.has("latest") && !pkg.get("latest").isJsonNull() ? pkg.get("latest").getAsString() : "";
                result.add(new RegistryPackage(normalize(pkg.get("name").getAsString()), latest));
            }
            packages = new Cached<>(List.copyOf(result), System.currentTimeMillis());
            return packages.value();
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
            LOG.debug("Could not list registry packages", e);
            return cached != null ? cached.value() : List.of();
        }
    }

    /** Published versions of a package, newest last as returned by the registry. */
    public @NotNull List<String> listVersions(@NotNull String id) {
        var cached = versions.get(id);
        if (cached != null && cached.fresh()) return cached.value();
        try {
            var json = JsonParser.parseString(HttpRequests.request(BASE_URL + "/packages/" + id)
                    .connectTimeout(3_000).readTimeout(5_000).readString()).getAsJsonObject();
            var result = new ArrayList<String>();
            for (var version : json.getAsJsonArray("versions")) result.add(version.getAsString());
            versions.put(id, new Cached<>(List.copyOf(result), System.currentTimeMillis()));
            return result;
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
            LOG.debug("Could not list versions of " + id, e);
            return cached != null ? cached.value() : List.of();
        }
    }

    /** The registry may report names as {@code @scope/name}; manifests use {@code scope/name}. */
    private static String normalize(String name) {
        return name.startsWith("@") ? name.substring(1) : name;
    }
}
