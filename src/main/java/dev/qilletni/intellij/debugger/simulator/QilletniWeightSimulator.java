package dev.qilletni.intellij.debugger.simulator;

import dev.qilletni.intellij.debugger.dap.WeightPickedEventArguments;
import dev.qilletni.intellij.debugger.weights.EntrySpan;
import dev.qilletni.intellij.debugger.weights.QilletniWeightStats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Simulates picks from a weights value the way the interpreter makes them, without running anything: function
 * entries are never called, and only songs already known from the debug session are used.
 * <p>
 * Mirrors {@code WeightDispersion} and {@code DefaultTrackOrchestrator.selectWeightedTrack}:
 * <ul>
 *     <li>Only percent entries are rolled, scaled to whole numbers, and entry <i>i</i> owns {@code [total(i-1), total(i))}</li>
 *     <li>At the top level, a roll past every entry is a miss, played from the collection's own queue</li>
 *     <li>Nested weights reroll a miss, and never reject a pick</li>
 *     <li>At the top level, {@code |} won't repeat the last song and {@code |~} won't repeat the last song or entry. A
 *     {@code |} pick leaves an earlier {@code |~} entry blocked, and a miss clears both</li>
 * </ul>
 */
public final class QilletniWeightSimulator {
    /** Rerolls in a row before giving up, as every entry is blocked by repeat rules. */
    public static final int MAX_REROLLS = 1_000;
    /** The most rerolls kept on one pick. */
    public static final int MAX_KEPT_REROLLS = 20;

    /**
     * @param unknown If the song isn't known, e.g. a function entry with no real picks yet, or songs not loaded
     */
    public record Song(@NotNull String id, @NotNull String text, boolean unknown) {}

    public enum Reason {SAME_SONG, SAME_ENTRY}

    /** A pick thrown away by a repeat rule. */
    public record Reroll(@NotNull List<Integer> indices, @NotNull Song song, @NotNull Reason reason) {}

    /**
     * @param indices     The entry's index at each level, outermost first, or empty for a miss
     * @param song        The song picked, or {@code null} for a miss
     * @param rerolls     The first {@link #MAX_KEPT_REROLLS} rerolls before this pick
     * @param rerollCount Every reroll before this pick
     */
    public record SimPick(@NotNull List<Integer> indices, @Nullable Song song, @NotNull List<Reroll> rerolls, int rerollCount) {

        public boolean miss() {
            return indices.isEmpty();
        }

        public int top() {
            return indices.isEmpty() ? -1 : indices.getFirst();
        }
    }

    /**
     * @param blocked If the simulation stopped early because every entry was blocked by repeat rules
     */
    public record Result(@NotNull List<SimPick> picks, boolean blocked) {}

    /**
     * An entry as the simulator sees it.
     *
     * @param index       The index in its weights
     * @param key         The index path, e.g. {@code 4/1}
     * @param separator   {@code |}, {@code |!} or {@code |~}, or {@code null} if unknown
     * @param kind        {@code song}, {@code list}, {@code collection}, {@code album}, {@code weights}, {@code function} or
     *                    {@code multiplier}
     * @param songs       The songs a leaf picks from; one unknown song if they aren't known
     * @param songWeights How often each song is picked relative to the others, or {@code null} for evenly
     * @param songsKnown  If {@code songs} are the real songs
     * @param approximate If the songs are simulated as evenly spread, but the entry has its own weights
     * @param span        Where the entry is written, or {@code null} if unknown
     */
    public record Entry(int index, @NotNull String key, @NotNull String name, @Nullable String separator, @NotNull String kind,
                        @NotNull String source, double amount, double combined, boolean sequential, int songCount,
                        @NotNull List<Entry> children, @NotNull List<Song> songs, int @Nullable [] songWeights,
                        boolean songsKnown, boolean approximate, @Nullable EntrySpan span) {

        public boolean multiplier() {
            return "multiplier".equals(kind);
        }

        public boolean leaf() {
            return children.isEmpty();
        }
    }

    /** A song of a focused entry, in order, with whether it's under nested weights. */
    public record FocusSong(@NotNull Song song, boolean nested) {}

    /** The repeat rules' memory and sequential positions, carried from one pick to the next. */
    public static final class State {
        private @Nullable String lastSong;
        private int lastEntry = -1;
        private final Map<String, Integer> sequentialIndex = new HashMap<>();
    }

    private record Dispersion(List<Entry> rolled, int[] cumulative, int scale) {}

    private static final class BlockedException extends Exception {
        BlockedException() {
            super(null, null, false, false);
        }
    }

    private final Random random;
    private final List<Entry> roots;
    private final Dispersion top;
    private final Map<String, Dispersion> nested = new HashMap<>();

    public QilletniWeightSimulator(@NotNull QilletniWeightStats.SimulationInput input, @NotNull Random random) {
        this.random = random;
        var breakdown = input.breakdown();
        this.roots = breakdown == null || breakdown.roots == null ? List.of()
                : breakdown.roots.stream().map(node -> entry(node, "", input)).toList();
        this.top = dispersion(roots);
    }

    public @NotNull List<Entry> roots() {
        return roots;
    }

    public @NotNull State newState() {
        return new State();
    }

    /** Makes {@code count} more picks, or fewer if every entry becomes blocked. */
    public @NotNull Result simulate(@NotNull State state, int count) {
        var picks = new ArrayList<SimPick>(count);
        try {
            for (int i = 0; i < count; i++) picks.add(pick(state));
            return new Result(picks, false);
        } catch (BlockedException e) {
            return new Result(picks, true);
        }
    }

    /**
     * @return The share of picks of each root entry, in the order of {@link #roots()}, then the share of misses, all in
     * percent; or {@code null} if every entry became blocked
     */
    public double @Nullable [] longRun(int count) {
        var shares = new double[roots.size() + 1];
        var state = newState();
        try {
            for (int i = 0; i < count; i++) {
                var pick = pick(state);
                shares[pick.miss() ? roots.size() : rootPosition(pick.top())]++;
            }
        } catch (BlockedException e) {
            return null;
        }

        for (int i = 0; i < shares.length; i++) shares[i] = shares[i] * 100 / count;
        return shares;
    }

    /** @return The position in {@link #roots()} of the root entry with {@code index}, or -1 */
    public int rootPosition(int index) {
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).index() == index) return i;
        }
        return -1;
    }

    /** @return Every song under {@code entry}, leaf by leaf, those under nested weights marked */
    public static @NotNull List<FocusSong> focusSongs(@NotNull Entry entry) {
        var songs = new ArrayList<FocusSong>();
        collectFocusSongs(entry, false, songs);
        return songs;
    }

    private static void collectFocusSongs(Entry entry, boolean nested, List<FocusSong> songs) {
        if (entry.multiplier()) return;
        if (entry.leaf()) {
            entry.songs().forEach(song -> songs.add(new FocusSong(song, nested)));
            return;
        }
        entry.children().forEach(child -> collectFocusSongs(child, true, songs));
    }

    private SimPick pick(State state) throws BlockedException {
        var rerolls = new ArrayList<Reroll>();
        var rerollCount = 0;
        for (int attempt = 0; attempt <= MAX_REROLLS; attempt++) {
            var rolled = roll(top);
            if (rolled < 0) {
                state.lastSong = null;
                state.lastEntry = -1;
                return new SimPick(List.of(), null, List.copyOf(rerolls), rerollCount);
            }

            var entry = top.rolled().get(rolled);
            var indices = new ArrayList<Integer>();
            indices.add(entry.index());
            var song = resolve(entry, indices, state);

            var sameEntry = entry.index() == state.lastEntry;
            var sameSong = !song.unknown() && song.id().equals(state.lastSong);
            if (sameEntry || sameSong) {
                rerollCount++;
                if (rerolls.size() < MAX_KEPT_REROLLS) {
                    rerolls.add(new Reroll(List.copyOf(indices), song, sameEntry ? Reason.SAME_ENTRY : Reason.SAME_SONG));
                }
                continue;
            }

            var songId = song.unknown() ? null : song.id();
            if ("|~".equals(entry.separator())) {
                state.lastSong = songId;
                state.lastEntry = entry.index();
            } else if (!"|!".equals(entry.separator())) {
                state.lastSong = songId;
            }
            return new SimPick(List.copyOf(indices), song, List.copyOf(rerolls), rerollCount);
        }

        throw new BlockedException();
    }

    /** Picks a song from {@code entry}, adding the index of each nested entry chosen on the way. */
    private Song resolve(Entry entry, List<Integer> indices, State state) throws BlockedException {
        if (!entry.leaf()) {
            var dispersion = nested.computeIfAbsent(entry.key(), key -> dispersion(entry.children()));
            for (int attempt = 0; attempt <= MAX_REROLLS; attempt++) {
                var rolled = roll(dispersion);
                if (rolled >= 0) {
                    var child = dispersion.rolled().get(rolled);
                    indices.add(child.index());
                    return resolve(child, indices, state);
                }
            }
            throw new BlockedException();
        }

        var songs = entry.songs();
        if (songs.size() == 1) return songs.getFirst();
        if (entry.sequential() && entry.songsKnown()) {
            int index = state.sequentialIndex.merge(entry.key(), 1, Integer::sum) - 1;
            return songs.get(index % songs.size());
        }

        var weights = entry.songWeights();
        if (weights != null) {
            var total = 0;
            for (var weight : weights) total += weight;
            var roll = random.nextInt(total);
            for (int i = 0; i < weights.length; i++) {
                roll -= weights[i];
                if (roll < 0) return songs.get(i);
            }
        }
        return songs.get(random.nextInt(songs.size()));
    }

    /** @return The position in {@code dispersion.rolled()} of the entry rolled, or -1 for a miss */
    private int roll(Dispersion dispersion) {
        var cumulative = dispersion.cumulative();
        var roll = random.nextInt(100 * dispersion.scale());
        for (int i = 0; i < cumulative.length; i++) {
            if (cumulative[i] > roll) return i;
        }
        return -1;
    }

    private static Dispersion dispersion(List<Entry> entries) {
        var rolled = entries.stream().filter(entry -> !entry.multiplier()).toList();

        var maxDecimals = 0;
        for (var entry : rolled) {
            var text = Double.toString(entry.amount());
            maxDecimals = Math.max(maxDecimals, text.length() - text.indexOf('.') - 1);
        }
        var scale = (int) Math.pow(10, maxDecimals);

        var cumulative = new int[rolled.size()];
        var total = 0;
        for (int i = 0; i < rolled.size(); i++) {
            total += (int) (rolled.get(i).amount() * scale);
            cumulative[i] = total;
        }
        return new Dispersion(rolled, cumulative, scale);
    }

    private static Entry entry(WeightPickedEventArguments.Node node, String parentKey, QilletniWeightStats.SimulationInput input) {
        var key = parentKey.isEmpty() ? String.valueOf(node.index) : parentKey + "/" + node.index;
        var name = node.name != null ? node.name : "[" + node.index + "]";
        var kind = node.kind != null ? node.kind : "song";
        var source = node.source != null ? node.source : "";
        var songCount = node.songCount > 0 ? node.songCount : node.listSize;
        var span = EntrySpan.of(node);

        if (node.children != null && !node.children.isEmpty()) {
            var children = node.children.stream().map(child -> entry(child, key, input)).toList();
            return new Entry(node.index, key, name, QilletniWeightStats.separator(name), kind, source, node.amount, node.combined,
                    false, songCount, children, List.of(), null, true, false, span);
        }

        var unknown = List.of(new Song("unknown:" + key, QilletniWeightStats.shortSource(kind, source, songCount), true));
        var seen = input.seenSongs().getOrDefault(key, List.of());
        List<Song> songs = unknown;
        int[] songWeights = null;
        var songsKnown = false;
        var approximate = false;

        switch (kind) {
            case "song" -> {
                var mostSeen = seen.stream().max(Comparator.comparingInt(QilletniWeightStats.SeenSong::count));
                songs = List.of(mostSeen.map(s -> new Song(s.trackId(), s.song(), false)).orElse(new Song("song:" + key, source, false)));
                songsKnown = true;
            }
            case "list", "album", "collection" -> {
                var loaded = input.entrySongs().get(key);
                if (loaded != null && loaded.songs != null && !loaded.songs.isEmpty()) {
                    songs = loaded.songs.stream()
                            .map(s -> {
                                var text = s.song != null ? s.song : String.valueOf(s.trackId);
                                return new Song(s.trackId != null ? s.trackId : text, text, false);
                            })
                            .toList();
                    songsKnown = true;
                    approximate = !loaded.evenChance;
                }
            }
            case "function" -> {
                if (!seen.isEmpty()) {
                    songs = seen.stream().map(s -> new Song(s.trackId(), s.song(), false)).toList();
                    songWeights = seen.stream().mapToInt(QilletniWeightStats.SeenSong::count).toArray();
                    songsKnown = true;
                }
            }
            default -> {
            }
        }

        return new Entry(node.index, key, name, QilletniWeightStats.separator(name), kind, source, node.amount, node.combined,
                node.sequential, songCount, List.of(), songs, songWeights, songsKnown, approximate, span);
    }
}