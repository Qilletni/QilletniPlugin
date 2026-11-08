package dev.qilletni.intellij.settings;

import dev.qilletni.intellij.toolchain.QilletniInstallation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Reads and writes package configuration in {@code ~/.qilletni/persistence/<package>.properties}, the same files
 * {@code qilletni persist <package> key=value} manages and Qilletni packages read at runtime. Keys the IDE doesn't
 * know about (such as cached credentials) are always preserved.
 */
public final class QilletniPersistence {
    private QilletniPersistence() {}

    public static @NotNull Path file(@NotNull String packageName) {
        return QilletniInstallation.persistenceDir().resolve(packageName + ".properties");
    }

    public static @NotNull Properties load(@NotNull String packageName) {
        var properties = new Properties();
        var file = file(packageName);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
                properties.load(reader);
            } catch (IOException ignored) {
                // Unreadable config behaves like an empty one
            }
        }
        return properties;
    }

    public static @Nullable String get(@NotNull String packageName, @NotNull String key) {
        var value = load(packageName).getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Sets the given keys, removing those mapped to {@code null} or blank, and leaves every other key untouched. */
    public static void update(@NotNull String packageName, @NotNull Map<String, @Nullable String> values) throws IOException {
        var properties = load(packageName);
        boolean changed = false;
        for (var entry : values.entrySet()) {
            var value = entry.getValue() == null || entry.getValue().isBlank() ? null : entry.getValue().trim();
            var old = properties.getProperty(entry.getKey());
            if (value == null ? old != null : !value.equals(old)) {
                if (value == null) properties.remove(entry.getKey());
                else properties.setProperty(entry.getKey(), value);
                changed = true;
            }
        }
        if (!changed) return;
        var file = file(packageName);
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.ISO_8859_1)) {
            properties.store(writer, "Properties for the package '" + packageName + "'");
        }
    }
}
