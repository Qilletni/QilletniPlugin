package dev.qilletni.intellij.library.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A semantic version of a Qilletni package, such as {@code 1.0.0} or {@code 1.0.0-SNAPSHOT}.
 * A version with a qualifier sorts before the same version without one.
 */
public record QllVersion(int major, int minor, int patch, @Nullable String qualifier) implements Comparable<QllVersion> {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?");

    private static final Comparator<QllVersion> ORDER = Comparator.comparingInt(QllVersion::major)
            .thenComparingInt(QllVersion::minor)
            .thenComparingInt(QllVersion::patch)
            .thenComparing(QllVersion::qualifier, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Parses a version string such as {@code 1.0.0} or {@code 1.0.0-SNAPSHOT}.
     *
     * @return The parsed version, or empty if the string is not a valid version
     */
    public static Optional<QllVersion> parse(@Nullable String versionString) {
        if (versionString == null) return Optional.empty();
        var matcher = PATTERN.matcher(versionString.trim());
        if (!matcher.matches()) return Optional.empty();
        try {
            return Optional.of(new QllVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(4)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether this version satisfies a dependency range as written in {@code qilletni_info.yml}: {@code ^1.2.3}
     * (same major, at least 1.2.3), {@code ~1.2.3} (same major and minor), {@code >=1.2.3}, an exact version,
     * or blank/{@code *} for any version.
     */
    public boolean satisfies(@Nullable String range) {
        if (range == null || range.isBlank() || range.trim().equals("*")) return true;
        var r = range.trim();
        if (r.startsWith(">=")) return parse(r.substring(2)).map(min -> compareTo(min) >= 0).orElse(false);
        if (r.startsWith("^")) {
            return parse(r.substring(1)).map(min -> major == min.major
                    && (major != 0 || minor == min.minor)
                    && compareTo(min) >= 0).orElse(false);
        }
        if (r.startsWith("~")) {
            return parse(r.substring(1)).map(min -> major == min.major && minor == min.minor && compareTo(min) >= 0).orElse(false);
        }
        return parse(r).map(this::equals).orElse(false);
    }

    @Override
    public int compareTo(@NotNull QllVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public @NotNull String toString() {
        var base = major + "." + minor + "." + patch;
        return qualifier == null ? base : base + "-" + qualifier;
    }
}
