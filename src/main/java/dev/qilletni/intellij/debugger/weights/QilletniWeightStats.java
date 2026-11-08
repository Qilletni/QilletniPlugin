package dev.qilletni.intellij.debugger.weights;

import dev.qilletni.intellij.debugger.dap.WeightEntrySongsResponse;
import dev.qilletni.intellij.debugger.dap.WeightPickedEventArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Counts of the weighted picks in a debug session, per outermost weights, compared with each entry's chance.
 * Thread-safe: events are added on the DAP event thread and views are read on the EDT.
 * <p>
 * Every song is counted, but a view only shows the {@link #TOP_SONGS} most picked songs under an entry, so a
 * collection with thousands of songs never becomes thousands of rows.
 */
public final class QilletniWeightStats {
    public static final int TOP_SONGS = 20;
    static final String MISS_KEY = "miss";
    private static final Set<String> SONG_KINDS = Set.of("list", "collection", "album");
    /** Shown automatically when small enough; collections only on request. */
    private static final Set<String> AUTO_SONG_KINDS = Set.of("list", "album");
    /** An entry's name, e.g. {@code [1] |~30%}: the separator, then the amount. */
    private static final Pattern ENTRY_NAME = Pattern.compile("^\\[\\d+] (\\|[!~]?)(.*)$");

    public enum Kind {ENTRY, MULTIPLIER, SONG, OTHERS, MISS, OMITTED}

    /**
     * @param key       Unique within a view: entry index paths like {@code 0/1}, with songs under them
     * @param separator An entry's separator ({@code |}, {@code |!} or {@code |~}), or {@code null}
     * @param text      Short display text, e.g. {@code list (4 songs)}
     * @param tooltip   The full text, e.g. the entry's name and everything it picks from
     * @param expected  The chance of this row, in percent, or {@code NaN} if unknown
     * @param actual    This row's share of all picks, in percent
     * @param count     Picks through this row
     * @param rejected  Picks of this row thrown away by {@code |!} or {@code |~}
     * @param entryKind An entry's kind, e.g. {@code list} or {@code collection}, or {@code null} for other rows
     * @param songCount The number of songs a list, collection or album entry picks from, otherwise 0
     * @param songsShown If every song of the entry is shown, rather than only the most picked ones
     * @param spans     Where the row's entry and the entries around it are written, outermost first, as far as known
     */
    public record Row(@NotNull String key, @NotNull Kind kind, @Nullable String separator, @NotNull String text,
                      @NotNull String tooltip, double expected, double actual, int count, int rejected,
                      @NotNull List<Row> children, @Nullable String entryKind, int songCount, boolean songsShown,
                      @NotNull List<EntrySpan> spans) {

        Row(@NotNull String key, @NotNull Kind kind, @Nullable String separator, @NotNull String text, @NotNull String tooltip,
            double expected, double actual, int count, int rejected, @NotNull List<Row> children) {
            this(key, kind, separator, text, tooltip, expected, actual, count, rejected, children, null, 0, false, List.of());
        }

        Row withSpans(@NotNull List<EntrySpan> spans) {
            return new Row(key, kind, separator, text, tooltip, expected, actual, count, rejected, children, entryKind, songCount, songsShown, spans);
        }

        /** If this entry's songs can be fetched with {@link #markRequested} and aren't shown yet. */
        public boolean canShowSongs() {
            return kind == Kind.ENTRY && songCount > 0 && !songsShown && SONG_KINDS.contains(entryKind);
        }
    }

    /** A {@code qilletni/weightEntrySongs} request to make. */
    public record SongRequest(int weightsId, @NotNull String key, @NotNull List<Integer> indices) {}

    /**
     * @param lastPick     The last pick's path with its rolls, then the song
     * @param lastRejected The last pick's rejected attempts
     */
    public record View(int id, @NotNull String label, int total, int misses, @NotNull List<Row> rows,
                       @Nullable String lastPick, @NotNull List<String> lastRejected) {}

    public record Summary(int id, @NotNull String label, int total) {}

    /**
     * A real pick, in the order they happened.
     *
     * @param key The picked entry's index path, e.g. {@code 4/1}, or {@link #MISS_KEY}
     */
    public record HistoryPick(@NotNull String key, @NotNull String trackId, @NotNull String song) {

        public boolean miss() {
            return MISS_KEY.equals(key);
        }
    }

    /** A song seen in real picks of one entry. */
    public record SeenSong(@NotNull String trackId, @NotNull String song, int count) {}

    /**
     * Everything known about one weights value, for simulating it. Never changes once made.
     *
     * @param breakdown  The entries and their chances, or {@code null} before the adapter sent them
     * @param entrySongs Every song of list, collection and album entries, by row key, for those fetched
     * @param seenSongs  Songs seen in real picks, by row key
     * @param history    The real picks, oldest first, at most {@link #MAX_HISTORY}
     */
    public record SimulationInput(int id, @NotNull String label, WeightPickedEventArguments.@Nullable Breakdown breakdown,
                                  @NotNull Map<String, WeightEntrySongsResponse> entrySongs,
                                  @NotNull Map<String, List<SeenSong>> seenSongs, @NotNull List<HistoryPick> history) {}

    public static final int MAX_HISTORY = 5_000;

    private final Map<Integer, Weights> weights = new LinkedHashMap<>();
    private int latestId;

    public synchronized void add(@NotNull WeightPickedEventArguments pick) {
        var w = weights.computeIfAbsent(pick.weightsId, id -> new Weights());
        if (pick.label != null) w.label = pick.label;
        if (pick.breakdown != null) w.breakdown = pick.breakdown;
        w.total++;
        w.last = pick;
        latestId = pick.weightsId;

        String leafKey;
        if (MISS_KEY.equals(pick.kind)) {
            w.misses++;
            leafKey = MISS_KEY;
        } else {
            var key = new StringBuilder();
            for (var level : orEmpty(pick.path)) {
                if (!key.isEmpty()) key.append('/');
                key.append(level.index);
                w.picks.merge(key.toString(), 1, Integer::sum);
            }
            leafKey = key.toString();
        }

        for (var rejected : orEmpty(pick.rejected)) {
            if (rejected.indices != null && !rejected.indices.isEmpty()) {
                w.rejected.merge(key(rejected.indices), 1, Integer::sum);
            }
        }

        var songId = pick.trackId != null ? pick.trackId : pick.song;
        if (songId != null) {
            var song = pick.song != null ? pick.song : songId;
            w.songs.computeIfAbsent(leafKey, k -> new HashMap<>())
                    .computeIfAbsent(songId, k -> new SongCount(song))
                    .count++;
            w.history.addLast(new HistoryPick(leafKey, songId, song));
            if (w.history.size() > MAX_HISTORY) w.history.removeFirst();
        }
    }

    public synchronized @Nullable SimulationInput simulationInput(int id) {
        var w = weights.get(id);
        if (w == null) return null;

        var seen = new HashMap<String, List<SeenSong>>();
        w.songs.forEach((key, songs) -> seen.put(key, songs.entrySet().stream()
                .map(entry -> new SeenSong(entry.getKey(), entry.getValue().song, entry.getValue().count))
                .sorted(Comparator.comparing(SeenSong::trackId))
                .toList()));
        return new SimulationInput(id, w.label(), w.breakdown, Map.copyOf(w.entrySongs), Map.copyOf(seen), List.copyOf(w.history));
    }

    /**
     * Marks the songs of an entry as requested, so they're only fetched once.
     *
     * @return If they weren't requested before
     */
    public synchronized boolean markRequested(int weightsId, @NotNull String key) {
        var w = weights.get(weightsId);
        return w != null && w.requested.add(key);
    }

    /** The request failed, so it may be made again. */
    public synchronized void requestFailed(int weightsId, @NotNull String key) {
        var w = weights.get(weightsId);
        if (w != null) w.requested.remove(key);
    }

    public synchronized void setEntrySongs(int weightsId, @NotNull String key, @NotNull WeightEntrySongsResponse songs) {
        var w = weights.get(weightsId);
        if (w != null) w.entrySongs.put(key, songs);
    }

    /**
     * Lists and albums with at most {@code limit} songs whose songs haven't been requested yet. They're marked as
     * requested.
     */
    public synchronized @NotNull List<SongRequest> autoRequests(int limit) {
        var requests = new ArrayList<SongRequest>();
        for (var entry : weights.entrySet()) {
            var breakdown = entry.getValue().breakdown;
            if (breakdown != null) autoRequests(entry.getKey(), entry.getValue(), orEmpty(breakdown.roots), "", limit, requests);
        }
        return requests;
    }

    private static void autoRequests(int id, Weights w, List<WeightPickedEventArguments.Node> nodes, String parentKey, int limit, List<SongRequest> requests) {
        for (var node : nodes) {
            var key = parentKey.isEmpty() ? String.valueOf(node.index) : parentKey + "/" + node.index;
            if (node.children != null && !node.children.isEmpty()) {
                autoRequests(id, w, node.children, key, limit, requests);
            } else if (AUTO_SONG_KINDS.contains(node.kind) && node.songCount > 0 && node.songCount <= limit && w.requested.add(key)) {
                requests.add(new SongRequest(id, key, indices(key)));
            }
        }
    }

    /** The entry indices of a row key, e.g. {@code 0/1}. */
    public static @NotNull List<Integer> indices(@NotNull String key) {
        return Arrays.stream(key.split("/")).map(Integer::valueOf).toList();
    }

    /**
     * @return Where the entries of the most recent pick are written, outermost first; empty for a miss or if unknown
     */
    public synchronized @NotNull List<EntrySpan> lastPickSpans() {
        var w = weights.get(latestId);
        if (w == null || w.last == null || w.breakdown == null || MISS_KEY.equals(w.last.kind)) return List.of();

        var spans = new ArrayList<EntrySpan>();
        var nodes = orEmpty(w.breakdown.roots);
        for (var level : orEmpty(w.last.path)) {
            var node = nodes.stream().filter(n -> n.index == level.index).findFirst().orElse(null);
            if (node == null) break;
            var span = EntrySpan.of(node);
            if (span != null) spans.add(span);
            nodes = orEmpty(node.children);
        }
        return spans;
    }

    /** The id of the weights picked from most recently, or 0 before any pick. */
    public synchronized int latestId() {
        return latestId;
    }

    /** Every weights picked from, in the order they were first picked. */
    public synchronized @NotNull List<Summary> summaries() {
        return weights.entrySet().stream()
                .map(entry -> new Summary(entry.getKey(), entry.getValue().label(), entry.getValue().total))
                .toList();
    }

    public synchronized @Nullable View view(int id) {
        var w = weights.get(id);
        if (w == null) return null;

        var rows = new ArrayList<Row>();
        var breakdown = w.breakdown;
        if (breakdown != null) {
            for (var node : orEmpty(breakdown.roots)) rows.add(entryRow(w, node, "", List.of()));
            if (breakdown.omitted > 0) {
                var text = "… %s more".formatted(count(breakdown.omitted));
                rows.add(new Row("omitted", Kind.OMITTED, null, text, text + " entries not shown", breakdown.omittedChance, 0, 0, 0, List.of()));
            }
        }

        var missChance = breakdown != null ? breakdown.miss : Double.NaN;
        if (w.misses > 0 || missChance > 0) {
            rows.add(new Row(MISS_KEY, Kind.MISS, null, "miss → the collection's own tracks",
                    "No entry was rolled, so a track from the collection's own queue played", missChance, share(w.misses, w.total),
                    w.misses, 0, songRows(w, MISS_KEY, Double.NaN)));
        }

        return new View(id, w.label(), w.total, w.misses, rows, lastPick(w.last), lastRejected(w.last));
    }

    /**
     * @return e.g. {@code 15}, {@code 33.33} or {@code 0.0033}: 2 decimals, or 2 significant digits below 1
     */
    public static @NotNull String number(double value) {
        if (value == 0 || !Double.isFinite(value)) return value == 0 ? "0" : "?";
        var decimals = Math.abs(value) >= 1 ? 2 : Math.max(2, 1 - (int) Math.floor(Math.log10(Math.abs(value))));
        return new BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static @NotNull String percent(double value) {
        return number(value) + "%";
    }

    public static @NotNull String count(int count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    /**
     * @return The separator of an entry name like {@code [1] |~30%}, e.g. {@code |~}, or {@code null}
     */
    public static @Nullable String separator(@Nullable String name) {
        if (name == null) return null;
        var parsed = ENTRY_NAME.matcher(name);
        return parsed.matches() ? parsed.group(1) : null;
    }

    private Row entryRow(Weights w, WeightPickedEventArguments.Node node, String parentKey, List<EntrySpan> parentSpans) {
        var key = parentKey.isEmpty() ? String.valueOf(node.index) : parentKey + "/" + node.index;
        var span = EntrySpan.of(node);
        var spans = new ArrayList<>(parentSpans);
        if (span != null) spans.add(span);
        var name = node.name != null ? node.name : "[" + node.index + "]";
        var source = node.source != null ? node.source : "";
        var tooltip = (name + " " + source).strip();
        var parsed = ENTRY_NAME.matcher(name);
        var separator = parsed.matches() ? parsed.group(1) : null;
        var amount = parsed.matches() ? parsed.group(2) : name;

        if ("multiplier".equals(node.kind)) {
            return new Row(key, Kind.MULTIPLIER, null, (amount + " " + source).strip(),
                    tooltip + " (copies in the collection's queue, not rolled)", Double.NaN, 0, 0, 0, List.of()).withSpans(List.copyOf(spans));
        }

        var songCount = node.songCount > 0 ? node.songCount : node.listSize;
        var loaded = w.entrySongs.get(key);
        List<Row> children;
        if (node.children != null && !node.children.isEmpty()) {
            children = node.children.stream().map(child -> entryRow(w, child, key, spans)).toList();
        } else if ("song".equals(node.kind) || "function".equals(node.kind) && !w.songs.containsKey(key)) {
            children = List.of();
        } else if (loaded != null) {
            var songs = orEmpty(loaded.songs);
            children = allSongRows(w, key, songs, loaded.evenChance && !songs.isEmpty() ? node.combined / songs.size() : Double.NaN);
        } else {
            children = songRows(w, key, "list".equals(node.kind) && songCount > 0 ? node.combined / songCount : Double.NaN);
        }

        var count = w.picks.getOrDefault(key, 0);
        var ownSpans = List.copyOf(spans);
        children = children.stream().map(child -> child.kind() == Kind.ENTRY ? child : child.withSpans(ownSpans)).toList();
        return new Row(key, Kind.ENTRY, separator, shortSource(node.kind, source, songCount), tooltip, node.combined,
                share(count, w.total), count, w.rejected.getOrDefault(key, 0), children, node.kind, songCount, loaded != null, ownSpans);
    }

    /**
     * @return {@code list (4 songs)} for a list, {@code source (12 songs)} for a collection or album, otherwise {@code source}
     */
    public static @NotNull String shortSource(@Nullable String kind, @NotNull String source, int songCount) {
        var songs = "%s %s".formatted(count(songCount), songCount == 1 ? "song" : "songs");
        if ("list".equals(kind)) return "list (%s)".formatted(songs);
        if (("collection".equals(kind) || "album".equals(kind)) && songCount > 0) return "%s (%s)".formatted(source, songs);
        return source;
    }

    /**
     * Every song of an entry in its own order, with how often each was picked, then any picked songs not among them.
     */
    private List<Row> allSongRows(Weights w, String key, List<WeightEntrySongsResponse.Song> songs, double expectedEach) {
        var counts = w.songs.getOrDefault(key, Map.of());
        var rows = new ArrayList<Row>(songs.size());
        var shown = new HashSet<String>();
        for (var song : songs) {
            var id = song.trackId != null ? song.trackId : song.song;
            if (id == null || !shown.add(id)) continue;
            var picked = counts.get(id);
            var count = picked != null ? picked.count : 0;
            var text = song.song != null ? song.song : id;
            rows.add(new Row(key + "#" + id, Kind.SONG, null, text, text, expectedEach, share(count, w.total), count, 0, List.of()));
        }

        counts.entrySet().stream()
                .filter(entry -> !shown.contains(entry.getKey()))
                .sorted(Comparator.<Map.Entry<String, SongCount>>comparingInt(entry -> entry.getValue().count).reversed())
                .forEach(entry -> rows.add(new Row(key + "#" + entry.getKey(), Kind.SONG, null, entry.getValue().song, entry.getValue().song,
                        Double.NaN, share(entry.getValue().count, w.total), entry.getValue().count, 0, List.of())));
        return rows;
    }

    /**
     * @param expectedEach The chance of each song, or {@code NaN} if unknown
     */
    private List<Row> songRows(Weights w, String key, double expectedEach) {
        var songs = w.songs.get(key);
        if (songs == null || songs.isEmpty()) return List.of();

        var sorted = songs.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, SongCount>>comparingInt(entry -> entry.getValue().count).reversed()
                        .thenComparing(entry -> entry.getValue().song))
                .toList();

        var rows = new ArrayList<Row>(Math.min(sorted.size(), TOP_SONGS + 1));
        for (var entry : sorted.subList(0, Math.min(TOP_SONGS, sorted.size()))) {
            var song = entry.getValue();
            rows.add(new Row(key + "#" + entry.getKey(), Kind.SONG, null, song.song, song.song, expectedEach,
                    share(song.count, w.total), song.count, 0, List.of()));
        }

        if (sorted.size() > TOP_SONGS) {
            var others = sorted.subList(TOP_SONGS, sorted.size());
            var picks = others.stream().mapToInt(entry -> entry.getValue().count).sum();
            var text = "… %s other songs".formatted(count(others.size()));
            rows.add(new Row(key + "#others", Kind.OTHERS, null, text, text + ", each picked less often than those above",
                    Double.NaN, share(picks, w.total), picks, 0, List.of()));
        }

        return rows;
    }

    private static @Nullable String lastPick(@Nullable WeightPickedEventArguments pick) {
        if (pick == null) return null;
        if (MISS_KEY.equals(pick.kind)) return "miss → " + pick.song;

        var levels = orEmpty(pick.path).stream()
                .map(level -> "%s (roll %s in [%s, %s))".formatted(level.name, number(level.roll), number(level.from), number(level.to)))
                .collect(Collectors.joining(" → "));
        return levels + " → " + pick.song;
    }

    private static List<String> lastRejected(@Nullable WeightPickedEventArguments pick) {
        if (pick == null) return List.of();

        var rejected = new ArrayList<String>();
        for (var r : orEmpty(pick.rejected)) {
            rejected.add("%s → %s (%s as last pick)".formatted(String.join(" → ", orEmpty(r.path)), r.song, r.reason));
        }
        if (pick.rejectedOmitted > 0) rejected.add("… %s more".formatted(count(pick.rejectedOmitted)));
        return rejected;
    }

    private static double share(int count, int total) {
        return total == 0 ? 0 : (double) count / total * 100;
    }

    private static String key(List<Integer> indices) {
        return indices.stream().map(String::valueOf).collect(Collectors.joining("/"));
    }

    private static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list != null ? list : List.of();
    }

    private static final class Weights {
        @Nullable String label;
        WeightPickedEventArguments.@Nullable Breakdown breakdown;
        int total;
        int misses;
        /** Picks through each entry, by index path. */
        final Map<String, Integer> picks = new HashMap<>();
        /** Rejected attempts, by the index path of the attempt's innermost entry. */
        final Map<String, Integer> rejected = new HashMap<>();
        /** Song counts under each leaf entry, or {@link #MISS_KEY}, by track id. */
        final Map<String, Map<String, SongCount>> songs = new HashMap<>();
        /** Every song of an entry, by row key, once fetched. */
        final Map<String, WeightEntrySongsResponse> entrySongs = new HashMap<>();
        /** Row keys whose songs were requested. */
        final Set<String> requested = new HashSet<>();
        /** The real picks, oldest first. */
        final ArrayDeque<HistoryPick> history = new ArrayDeque<>();
        @Nullable WeightPickedEventArguments last;

        String label() {
            return label != null ? label : "weights";
        }
    }

    private static final class SongCount {
        final String song;
        int count;

        SongCount(String song) {
            this.song = song;
        }
    }
}
