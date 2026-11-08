package dev.qilletni.intellij.library.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A parsed {@code qilletni-src/qilletni_info.yml}.
 *
 * @param name         the package name, possibly scoped ({@code scope/name})
 * @param dependencies dependency ids ({@code scope/name}) mapped to version ranges such as {@code ^1.0.0}
 */
public record QilletniManifest(
        @Nullable String name,
        @Nullable String version,
        @Nullable String author,
        @Nullable String description,
        @NotNull Map<String, String> dependencies,
        @NotNull List<String> nativeClasses
) {
    public static final String FILE_NAME = "qilletni_info.yml";

    /** The name without its scope, which is what {@code import "name:path.ql"} refers to. */
    public @Nullable String simpleName() {
        if (name == null) return null;
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    /** Parses manifest text; returns {@code null} if it is not a YAML map. */
    public static @Nullable QilletniManifest parse(@NotNull String text) {
        Object data;
        try {
            data = new Yaml().load(text);
        } catch (YAMLException e) {
            return null;
        }
        if (!(data instanceof Map<?, ?> map)) return null;

        var dependencies = new LinkedHashMap<String, String>();
        if (map.get("dependencies") instanceof Map<?, ?> deps) {
            deps.forEach((id, range) -> {
                if (id != null) dependencies.put(id.toString().trim(), range == null ? "" : range.toString().trim());
            });
        }

        List<String> nativeClasses = map.get("native_classes") instanceof List<?> list
                ? list.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();

        return new QilletniManifest(
                stringOf(map.get("name")),
                stringOf(map.get("version")),
                stringOf(map.get("author")),
                stringOf(map.get("description")),
                Collections.unmodifiableMap(dependencies),
                nativeClasses);
    }

    private static @Nullable String stringOf(@Nullable Object value) {
        if (value == null) return null;
        var s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
