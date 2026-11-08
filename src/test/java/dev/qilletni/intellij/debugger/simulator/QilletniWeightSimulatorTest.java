package dev.qilletni.intellij.debugger.simulator;

import dev.qilletni.intellij.debugger.dap.WeightEntrySongsResponse;
import dev.qilletni.intellij.debugger.dap.WeightPickedEventArguments;
import dev.qilletni.intellij.debugger.weights.QilletniWeightStats;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class QilletniWeightSimulatorTest extends TestCase {

    /** Rolls 0, 1, 2, … so every roll is made exactly once per range. */
    private static final class SequentialRandom extends Random {
        private int next;

        @Override
        public int nextInt(int bound) {
            return next++ % bound;
        }
    }

    public void testEvenSplitOverEveryRoll() {
        var simulator = simulator(List.of(song(0, "|!", 50), song(1, "|!", 50)), Map.of(), Map.of(), new SequentialRandom());
        var counts = topCounts(simulator.simulate(simulator.newState(), 1000).picks(), 2);
        assertEquals(List.of(500, 500), counts);
    }

    public void testZeroPercentEntryIsNeverPicked() {
        var simulator = simulator(List.of(song(0, "|!", 0), song(1, "|!", 100)), Map.of(), Map.of(), new SequentialRandom());
        var counts = topCounts(simulator.simulate(simulator.newState(), 1000).picks(), 2);
        assertEquals(List.of(0, 1000), counts);
    }

    public void testMultiplierIsNeverRolled() {
        var multiplier = song(0, "|", 5);
        multiplier.kind = "multiplier";
        multiplier.name = "[0] |5x";
        var simulator = simulator(List.of(multiplier, song(1, "|!", 50), song(2, "|!", 50)), Map.of(), Map.of(), new SequentialRandom());
        var counts = topCounts(simulator.simulate(simulator.newState(), 1000).picks(), 3);
        assertEquals(List.of(0, 500, 500), counts);
    }

    public void testTopLevelUnder100PercentMisses() {
        var simulator = simulator(List.of(song(0, "|!", 30)), Map.of(), Map.of(), new SequentialRandom());
        var picks = simulator.simulate(simulator.newState(), 1000).picks();
        assertEquals(300, picks.stream().filter(pick -> !pick.miss()).count());
        assertTrue(picks.subList(0, 300).stream().noneMatch(QilletniWeightSimulator.SimPick::miss));
        assertTrue(picks.subList(300, 1000).stream().allMatch(QilletniWeightSimulator.SimPick::miss));
    }

    public void testNoRepeatEntryNeverPicksTheSameEntryTwiceInARow() {
        var simulator = simulator(List.of(song(0, "|~", 50), song(1, "|~", 50)), Map.of(), Map.of(), new Random(1));
        var picks = simulator.simulate(simulator.newState(), 10_000).picks();
        for (int i = 1; i < picks.size(); i++) assertTrue(picks.get(i).top() != picks.get(i - 1).top());
    }

    public void testPlainSeparatorNeverPicksTheSameSongTwiceInARow() {
        var list = node(0, "|", 100, "list", List.of());
        var simulator = simulator(List.of(list), Map.of("0", songs("a", "b")), Map.of(), new Random(2));
        var picks = simulator.simulate(simulator.newState(), 10_000).picks();
        for (int i = 1; i < picks.size(); i++) assertFalse(picks.get(i).song().id().equals(picks.get(i - 1).song().id()));
    }

    public void testPlainPickLeavesTheLastNoRepeatEntryBlocked() {
        var list = node(1, "|", 50, "list", List.of());
        var simulator = simulator(List.of(song(0, "|~", 50), list), Map.of("1", songs("a", "b")), Map.of(), new Random(3));
        var picks = simulator.simulate(simulator.newState(), 1000).picks();
        assertEquals("the |~ entry stays blocked once picked, as in the interpreter", 1, picks.stream().filter(pick -> pick.top() == 0).count());
    }

    public void testMissClearsTheRepeatRules() {
        var simulator = simulator(List.of(song(0, "|~", 50)), Map.of(), Map.of(), new Random(4));
        var picks = simulator.simulate(simulator.newState(), 1000).picks();
        assertTrue(picks.stream().filter(pick -> pick.top() == 0).count() > 100);
        for (int i = 1; i < picks.size(); i++) {
            if (picks.get(i).top() == 0) assertTrue(picks.get(i - 1).miss());
        }
    }

    public void testEveryEntryBlockedStops() {
        var simulator = simulator(List.of(song(0, "|~", 100)), Map.of(), Map.of(), new Random(5));
        var result = simulator.simulate(simulator.newState(), 10);
        assertTrue(result.blocked());
        assertEquals(1, result.picks().size());
    }

    public void testSequentialAlbumCyclesInOrder() {
        var album = node(0, "|!", 100, "album", List.of());
        album.sequential = true;
        var simulator = simulator(List.of(album), Map.of("0", songs("a", "b", "c")), Map.of(), new Random(6));
        var picks = simulator.simulate(simulator.newState(), 7).picks();
        assertEquals(List.of("a", "b", "c", "a", "b", "c", "a"), picks.stream().map(pick -> pick.song().id()).toList());
    }

    public void testFunctionWithoutRealPicksIsUnknown() {
        var function = node(0, "|!", 100, "function", List.of());
        function.source = "mySongFunc()";
        var simulator = simulator(List.of(function), Map.of(), Map.of(), new Random(7));
        var pick = simulator.simulate(simulator.newState(), 1).picks().getFirst();
        assertTrue(pick.song().unknown());
        assertFalse(simulator.roots().getFirst().songsKnown());
    }

    public void testFunctionUsesOnlySongsSeenInRealPicks() {
        var function = node(0, "|!", 100, "function", List.of());
        var seen = Map.of("0", List.of(new QilletniWeightStats.SeenSong("x", "\"X\"", 3), new QilletniWeightStats.SeenSong("y", "\"Y\"", 1)));
        var simulator = simulator(List.of(function), Map.of(), seen, new Random(8));
        var ids = simulator.simulate(simulator.newState(), 1000).picks().stream().map(pick -> pick.song().id()).distinct().sorted().toList();
        assertEquals(List.of("x", "y"), ids);
    }

    public void testLongRunOfAlbsMatchesTheInterpreterRules() {
        var albums = List.of(node(0, "|~", 35, "album", List.of()), node(1, "|~", 30, "album", List.of()), node(2, "|~", 10, "album", List.of()));
        var function = node(3, "|", 5, "function", List.of());
        var child = node(4, "|~", 5, "weights", List.of(song(0, "|", 50), song(1, "|", 50)));
        var list = node(5, "|~", 15, "list", List.of());

        var entrySongs = new HashMap<String, WeightEntrySongsResponse>();
        entrySongs.put("0", songs(range("blisstonia", 6)));
        entrySongs.put("1", songs(range("escape", 7)));
        entrySongs.put("2", songs(range("tear", 6)));
        entrySongs.put("5", songs("orion", "nineteen", "apollo", "creatures"));
        var seen = Map.of("3", List.of(new QilletniWeightStats.SeenSong("god", "\"God Knows\"", 1), new QilletniWeightStats.SeenSong("palace", "\"The Palace\"", 1)),
                "4/0", List.of(new QilletniWeightStats.SeenSong("mango", "\"MANGO\"", 1)),
                "4/1", List.of(new QilletniWeightStats.SeenSong("reflections", "\"Reflections\"", 1)));

        var roots = new ArrayList<>(albums);
        roots.addAll(List.of(function, child, list));
        var shares = simulator(roots, entrySongs, seen, new Random(42)).longRun(200_000);
        assertNotNull(shares);
        var expected = new double[]{29.94, 27.79, 12.11, 6.6, 6.45, 17.11};
        for (int i = 0; i < expected.length; i++) {
            assertEquals("entry " + i + " in " + Arrays.toString(shares), expected[i], shares[i], 0.5);
        }
    }

    private static List<Integer> topCounts(List<QilletniWeightSimulator.SimPick> picks, int entries) {
        var counts = new int[entries];
        picks.forEach(pick -> {
            if (!pick.miss()) counts[pick.top()]++;
        });
        return Arrays.stream(counts).boxed().toList();
    }

    private static QilletniWeightSimulator simulator(List<WeightPickedEventArguments.Node> roots, Map<String, WeightEntrySongsResponse> entrySongs,
                                                     Map<String, List<QilletniWeightStats.SeenSong>> seen, Random random) {
        var breakdown = new WeightPickedEventArguments.Breakdown();
        breakdown.roots = roots;
        var input = new QilletniWeightStats.SimulationInput(1, "weights", breakdown, entrySongs, seen, List.of());
        return new QilletniWeightSimulator(input, random);
    }

    private static WeightPickedEventArguments.Node song(int index, String separator, double amount) {
        var node = node(index, separator, amount, "song", List.of());
        node.source = "\"Song " + index + "\"";
        return node;
    }

    private static WeightPickedEventArguments.Node node(int index, String separator, double amount, String kind, List<WeightPickedEventArguments.Node> children) {
        var node = new WeightPickedEventArguments.Node();
        node.index = index;
        node.name = "[%d] %s%s%%".formatted(index, separator, amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount));
        node.kind = kind;
        node.amount = amount;
        node.chance = amount;
        node.combined = amount;
        node.source = kind;
        node.children = children;
        return node;
    }

    private static String[] range(String prefix, int count) {
        var ids = new String[count];
        for (int i = 0; i < count; i++) ids[i] = prefix + i;
        return ids;
    }

    private static WeightEntrySongsResponse songs(String... ids) {
        var response = new WeightEntrySongsResponse();
        response.evenChance = true;
        response.songs = Arrays.stream(ids).map(id -> {
            var song = new WeightEntrySongsResponse.Song();
            song.trackId = id;
            song.song = "\"" + id + "\"";
            return song;
        }).toList();
        return response;
    }
}
