package dev.qilletni.intellij.library.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed {@code qilletni-src/qilletni.lock}, written by {@code qpm install}. It lists every package the project
 * needs, including transitive ones:
 * <pre>
 * packages:
 *   qilletni/std@1.0.0:
 *     version: 1.0.0
 *     resolved: qilletni/std/std-1.0.0
 * </pre>
 * {@code resolved} is a path relative to the packages directory, without the {@code .qll} extension.
 */
public record QilletniLockfile(@NotNull List<Entry> packages) {
    public static final String FILE_NAME = "qilletni.lock";

    /** @param id the package id, {@code scope/name} */
    public record Entry(@NotNull String id, @Nullable String version, @Nullable String resolved) {}

    public boolean contains(@NotNull String id) {
        return packages.stream().anyMatch(e -> e.id().equalsIgnoreCase(id));
    }

    /** Parses lockfile text; returns {@code null} if it is not a valid lockfile. */
    public static @Nullable QilletniLockfile parse(@NotNull String text) {
        Object data;
        try {
            data = new Yaml().load(text);
        } catch (YAMLException e) {
            return null;
        }
        if (!(data instanceof Map<?, ?> map)) return null;

        var entries = new ArrayList<Entry>();
        if (map.get("packages") instanceof Map<?, ?> packages) {
            packages.forEach((key, value) -> {
                if (key == null) return;
                var keyText = key.toString();
                int at = keyText.lastIndexOf('@');
                var id = at > 0 ? keyText.substring(0, at) : keyText;
                String version = at > 0 ? keyText.substring(at + 1) : null;
                String resolved = null;
                if (value instanceof Map<?, ?> details) {
                    if (details.get("version") != null) version = details.get("version").toString();
                    if (details.get("resolved") != null) resolved = details.get("resolved").toString();
                }
                entries.add(new Entry(id, version, resolved));
            });
        }
        return new QilletniLockfile(List.copyOf(entries));
    }
}
