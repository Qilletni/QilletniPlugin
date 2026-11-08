package dev.qilletni.intellij.library;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import dev.qilletni.intellij.library.model.InstalledQllLibrary;
import dev.qilletni.intellij.library.model.QllInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Application-level cache of .qll archives. Loading an archive reads its {@code qll.info}, extracts
 * {@code qilletni-src/**} into the IDE system directory and puts the native classes in a {@code native.jar} next to it,
 * so both can be used as library roots. Current archives store the classes under {@code native/}; older ones contain
 * a {@code native.jar}.
 * <p>
 * Which archives a project uses is decided by {@link QilletniProjectDependencies}; this class only loads them.
 */
@Service
public final class QilletniLibraryManager {
    private static final Logger LOG = Logger.getInstance(QilletniLibraryManager.class);
    private static final String SRC_PREFIX = "qilletni-src/";
    private static final String NATIVE_PREFIX = "native/";
    private static final String NATIVE_JAR = "native.jar";
    /** Bump when the extraction layout changes, so caches written by older versions are extracted again. */
    private static final int CACHE_FORMAT = 2;

    /** Result of loading an archive: either a library or an error message. */
    public record LoadResult(@Nullable InstalledQllLibrary library, @Nullable String error) {
        static LoadResult error(String message) { return new LoadResult(null, message); }
    }

    private record CacheKey(Path archive, long modified, long size) {}

    private final Gson gson = new Gson();
    private final Map<CacheKey, LoadResult> loaded = new ConcurrentHashMap<>();

    public static QilletniLibraryManager getInstance() {
        return ApplicationManager.getApplication().getService(QilletniLibraryManager.class);
    }

    /** Root directory of extracted archives. */
    public static @NotNull Path cacheRoot() {
        return Path.of(PathManager.getSystemPath(), "qilletni-libs");
    }

    /**
     * Loads the given archive, extracting it if it changed since the last load. Performs IO and VFS refreshes,
     * so call it from a background thread without holding a read action.
     */
    public @NotNull LoadResult load(@NotNull Path archive) {
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(archive, BasicFileAttributes.class);
        } catch (IOException e) {
            return LoadResult.error("Archive not found: " + archive);
        }
        var key = new CacheKey(archive.toAbsolutePath().normalize(), attrs.lastModifiedTime().toMillis(), attrs.size());
        var cached = loaded.get(key);
        if (cached != null && (cached.library() == null || isValid(cached.library()))) return cached;

        var result = doLoad(key);
        loaded.keySet().removeIf(k -> k.archive().equals(key.archive()));
        loaded.put(key, result);
        return result;
    }

    /** Reads only the {@code qll.info} of an archive. */
    public @Nullable QllInfo readInfo(@NotNull Path archive) {
        try (var zip = new ZipFile(archive.toFile())) {
            return readInfo(zip);
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }

    /** Forgets every loaded archive and deletes the extraction cache. */
    public void clearCache() throws IOException {
        loaded.clear();
        NioFiles.deleteRecursively(cacheRoot());
        VirtualFileManager.getInstance().asyncRefresh(null);
    }

    private static boolean isValid(InstalledQllLibrary lib) {
        return (lib.srcRoot() == null || lib.srcRoot().isValid())
                && (lib.nativeJarRoot() == null || lib.nativeJarRoot().isValid());
    }

    private LoadResult doLoad(CacheKey key) {
        var archive = key.archive();
        try (var zip = new ZipFile(archive.toFile())) {
            QllInfo info;
            try {
                info = readInfo(zip);
            } catch (JsonParseException e) {
                return LoadResult.error("Invalid qll.info in " + archive.getFileName() + ": " + e.getMessage());
            }
            if (info == null) return LoadResult.error("Missing qll.info in " + archive.getFileName());
            if (info.name() == null || info.name().isBlank()) return LoadResult.error("qll.info in " + archive.getFileName() + " has no name");

            var cacheDir = cacheDirFor(archive);
            var stamp = CACHE_FORMAT + ":" + key.modified() + ":" + key.size();
            var stampFile = cacheDir.resolve(".qll.meta");
            boolean upToDate = Files.isRegularFile(stampFile) && Files.readString(stampFile).equals(stamp);
            if (!upToDate) {
                NioFiles.deleteRecursively(cacheDir);
                Files.createDirectories(cacheDir);
                extract(zip, cacheDir);
                Files.writeString(stampFile, stamp);
            }

            var srcDir = cacheDir.resolve("qilletni-src");
            VirtualFile srcRoot = Files.isDirectory(srcDir) ? LocalFileSystem.getInstance().refreshAndFindFileByNioFile(srcDir) : null;
            VirtualFile nativeJarRoot = null;
            var nativeJar = cacheDir.resolve(NATIVE_JAR);
            var localJar = Files.isRegularFile(nativeJar) ? LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nativeJar) : null;
            if (localJar != null) {
                nativeJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(localJar);
                // A re-extracted jar replaces one at the same path; drop what the jar file system remembers of it
                if (nativeJarRoot != null && !upToDate) nativeJarRoot.refresh(false, true);
            }

            return new LoadResult(new InstalledQllLibrary(
                    info.scope(),
                    info.name(),
                    info.version(),
                    info.author(),
                    info.description(),
                    archive,
                    srcRoot,
                    nativeJarRoot,
                    info.nativeClasses() == null ? List.of() : List.copyOf(info.nativeClasses()),
                    info.autoImportFiles() == null ? List.of() : List.copyOf(info.autoImportFiles())
            ), null);
        } catch (IOException e) {
            LOG.warn("Failed to load Qilletni library " + archive, e);
            return LoadResult.error("Failed to read " + archive.getFileName() + ": " + e.getMessage());
        }
    }

    private @Nullable QllInfo readInfo(ZipFile zip) throws IOException {
        var entry = zip.getEntry("qll.info");
        if (entry == null) return null;
        try (var reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) throw new JsonParseException("qll.info is not a JSON object");
            var obj = json.getAsJsonObject();
            normalizeVersion(obj);
            if (obj.get("dependencies") != null && obj.get("dependencies").isJsonArray()) {
                for (var dep : obj.getAsJsonArray("dependencies")) {
                    if (dep.isJsonObject()) normalizeVersion(dep.getAsJsonObject());
                }
            }
            return gson.fromJson(obj, QllInfo.class);
        }
    }

    /** Older archives wrote versions as {@code {"major":1,"minor":0,"patch":0}}; newer ones as {@code "1.0.0"}. */
    private static void normalizeVersion(JsonObject obj) {
        JsonElement version = obj.get("version");
        if (version != null && version.isJsonObject()) {
            var v = version.getAsJsonObject();
            obj.add("version", new JsonPrimitive(intOf(v, "major") + "." + intOf(v, "minor") + "." + intOf(v, "patch")));
        }
    }

    private static int intOf(JsonObject obj, String key) {
        var e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
    }

    /** Directory the given archive is extracted into. */
    static @NotNull Path cacheDirFor(@NotNull Path archive) {
        return cacheRoot().resolve(sha256(archive.toAbsolutePath().normalize().toString()));
    }

    /**
     * Extracts {@code qilletni-src/**} and {@code native.jar}, refusing entries that would escape {@code target}.
     * Archives without a {@code native.jar} get one packed from their {@code native/**} entries.
     */
    private static void extract(ZipFile zip, Path target) throws IOException {
        var root = target.toAbsolutePath().normalize();
        if (zip.getEntry(NATIVE_JAR) == null) repackNativeDir(zip, root.resolve(NATIVE_JAR));
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var name = entry.getName();
            if (!name.startsWith(SRC_PREFIX) && !name.equals(NATIVE_JAR)) continue;

            var out = root.resolve(name).normalize();
            if (!out.startsWith(root)) {
                LOG.warn("Skipping archive entry outside extraction directory: " + name);
                continue;
            }
            if (entry.isDirectory()) {
                Files.createDirectories(out);
            } else {
                Files.createDirectories(out.getParent());
                try (var in = zip.getInputStream(entry)) {
                    Files.copy(in, out);
                }
            }
        }
    }

    /** Writes the archive's {@code native/**} entries, without that prefix, to a jar. Writes nothing if there are none. */
    private static void repackNativeDir(ZipFile zip, Path out) throws IOException {
        var nativeEntries = zip.stream()
                .filter(e -> e.getName().startsWith(NATIVE_PREFIX) && e.getName().length() > NATIVE_PREFIX.length())
                .toList();
        if (nativeEntries.isEmpty()) return;

        try (var jar = new JarOutputStream(Files.newOutputStream(out))) {
            for (var entry : nativeEntries) {
                jar.putNextEntry(new ZipEntry(entry.getName().substring(NATIVE_PREFIX.length())));
                if (!entry.isDirectory()) {
                    try (var in = zip.getInputStream(entry)) {
                        in.transferTo(jar);
                    }
                }
                jar.closeEntry();
            }
        }
    }

    private static String sha256(String s) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
