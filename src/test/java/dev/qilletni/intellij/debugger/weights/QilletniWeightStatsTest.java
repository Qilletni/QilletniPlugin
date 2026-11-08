package dev.qilletni.intellij.debugger.weights;

import dev.qilletni.intellij.debugger.dap.WeightEntrySongsResponse;
import dev.qilletni.intellij.debugger.dap.WeightPickedEventArguments;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class QilletniWeightStatsTest extends TestCase {
    private static final int SONGS = 3_000;

    public void testLargeCollectionKeepsCountsButShowsTopSongs() {
        var stats = new QilletniWeightStats();
        stats.add(pick(breakdown(50, 50), "song-a", 0, 0));

        // 7,000 picks of the collection entry, spread over 3,000 songs; song-0 is picked most
        for (int i = 0; i < 7_000; i++) {
            stats.add(pick(null, "song-" + (i < 100 ? 0 : i % SONGS), 1));
        }
        for (int i = 1; i < 3_000; i++) {
            stats.add(pick(null, i % 2 == 0 ? "song-a" : "song-b", 0, i % 2));
        }

        var view = stats.view(1);
        assertNotNull(view);
        assertEquals(10_000, view.total());

        var nested = row(view.rows(), "0");
        assertEquals(3_000, nested.count());
        assertEquals(30.0, nested.actual(), 1e-9);
        assertEquals(15.0, row(nested.children(), "0/0").expected(), 1e-9);
        assertEquals(1_500, row(nested.children(), "0/1").count());

        var collection = row(view.rows(), "1");
        assertEquals(7_000, collection.count());
        assertEquals(70.0, collection.expected(), 1e-9);
        assertEquals(QilletniWeightStats.TOP_SONGS + 1, collection.children().size());

        var top = collection.children().getFirst();
        assertEquals(QilletniWeightStats.Kind.SONG, top.kind());
        assertEquals("\"song-0\"", top.text());

        var others = collection.children().getLast();
        assertEquals(QilletniWeightStats.Kind.OTHERS, others.kind());
        var shown = collection.children().stream().filter(r -> r.kind() == QilletniWeightStats.Kind.SONG).mapToInt(QilletniWeightStats.Row::count).sum();
        assertEquals(7_000, shown + others.count());
        assertTrue(others.text().contains("2,980"));
    }

    public void testChangedBreakdownKeepsCounts() {
        var stats = new QilletniWeightStats();
        stats.add(pick(breakdown(50, 50), "song-a", 0, 0));
        stats.add(pick(breakdown(20, 80), "song-b", 0, 1));

        var nested = row(stats.view(1).rows(), "0");
        assertEquals(6.0, row(nested.children(), "0/0").expected(), 1e-9);
        assertEquals(24.0, row(nested.children(), "0/1").expected(), 1e-9);
        assertEquals(2, nested.count());
    }

    public void testMissesAndRejections() {
        var stats = new QilletniWeightStats();
        var first = pick(breakdown(50, 50), "song-a", 0, 0);
        first.breakdown.miss = 20;
        stats.add(first);

        var miss = pick(null, "queued");
        miss.kind = "miss";
        stats.add(miss);

        var rejected = pick(null, "song-b", 0, 1);
        var rejection = new WeightPickedEventArguments.Rejected();
        rejection.indices = List.of(0, 0);
        rejection.path = List.of("[0] |~30%", "[0] |50%");
        rejection.song = "\"song-a\"";
        rejection.reason = "same entry";
        rejected.rejected = List.of(rejection);
        stats.add(rejected);

        var view = stats.view(1);
        assertEquals(1, view.misses());
        var missRow = row(view.rows(), QilletniWeightStats.MISS_KEY);
        assertEquals(20.0, missRow.expected(), 1e-9);
        assertEquals(1, missRow.count());
        assertEquals("\"queued\"", missRow.children().getFirst().text());

        assertEquals(1, row(row(view.rows(), "0").children(), "0/0").rejected());
        assertEquals(List.of("[0] |~30% → [0] |50% → \"song-a\" (same entry as last pick)"), view.lastRejected());
    }

    public void testEntryText() {
        var list = node("[0] |~20%", 0, "list", 20, 20, List.of());
        list.source = "[\"Orion\" by \"Once Almost Never\", \"Apollo\" by \"Once Almost Never\"]";
        list.listSize = 4;
        var song = node("[1] |80%", 1, "song", 80, 80, List.of());
        song.source = "\"C\" by \"X\"";
        var multiplier = node("[2] |3x", 2, "multiplier", 0, 0, List.of());
        multiplier.source = "\"E\" by \"X\"";
        var breakdown = new WeightPickedEventArguments.Breakdown();
        breakdown.roots = List.of(list, song, multiplier);

        var stats = new QilletniWeightStats();
        stats.add(pick(breakdown, "c", 1));
        var rows = stats.view(1).rows();

        var listRow = row(rows, "0");
        assertEquals("|~", listRow.separator());
        assertEquals("list (4 songs)", listRow.text());
        assertTrue(listRow.tooltip(), listRow.tooltip().contains("Orion"));

        var songRow = row(rows, "1");
        assertEquals("|", songRow.separator());
        assertEquals("\"C\" by \"X\"", songRow.text());

        var multiplierRow = row(rows, "2");
        assertEquals(QilletniWeightStats.Kind.MULTIPLIER, multiplierRow.kind());
        assertNull(multiplierRow.separator());
        assertEquals("3x \"E\" by \"X\"", multiplierRow.text());
        assertTrue(multiplierRow.tooltip(), multiplierRow.tooltip().contains("not rolled"));
    }

    public void testEntrySongs() {
        var album = node("[0] |50%", 0, "album", 50, 50, List.of());
        album.source = "\"Blisstonia\" by \"X\"";
        album.songCount = 3;
        var collection = node("[1] |50%", 1, "collection", 50, 50, List.of());
        collection.source = "\"Big\" collection";
        collection.songCount = 3_000;
        var breakdown = new WeightPickedEventArguments.Breakdown();
        breakdown.roots = List.of(album, collection);

        var stats = new QilletniWeightStats();
        stats.add(pick(breakdown, "b", 0));

        var requests = stats.autoRequests(25);
        assertEquals(List.of(new QilletniWeightStats.SongRequest(1, "0", List.of(0))), requests);
        assertTrue("requests are only made once", stats.autoRequests(25).isEmpty());

        var collectionRow = row(stats.view(1).rows(), "1");
        assertEquals("\"Big\" collection (3,000 songs)", collectionRow.text());
        assertTrue(collectionRow.canShowSongs());

        var songs = new WeightEntrySongsResponse();
        songs.evenChance = true;
        songs.songs = List.of(song("a"), song("b"), song("c"));
        stats.setEntrySongs(1, "0", songs);

        var albumRow = row(stats.view(1).rows(), "0");
        assertTrue(albumRow.songsShown());
        assertFalse(albumRow.canShowSongs());
        assertEquals(List.of("\"a\"", "\"b\"", "\"c\""), albumRow.children().stream().map(QilletniWeightStats.Row::text).toList());
        assertEquals(List.of(0, 1, 0), albumRow.children().stream().map(QilletniWeightStats.Row::count).toList());
        assertEquals(50.0 / 3, albumRow.children().getFirst().expected(), 1e-9);
    }

    public void testNumbers() {
        assertEquals("15%", QilletniWeightStats.percent(15));
        assertEquals("33.33%", QilletniWeightStats.percent(100.0 / 3));
        assertEquals("0.0033%", QilletniWeightStats.percent(0.00333));
        assertEquals("0%", QilletniWeightStats.percent(0));
    }

    private static WeightEntrySongsResponse.Song song(String id) {
        var song = new WeightEntrySongsResponse.Song();
        song.song = "\"" + id + "\"";
        song.trackId = id;
        return song;
    }

    private static QilletniWeightStats.Row row(List<QilletniWeightStats.Row> rows, String key) {
        return rows.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow(() -> new AssertionError("No row " + key));
    }

    private static WeightPickedEventArguments pick(WeightPickedEventArguments.Breakdown breakdown, String trackId, int... indices) {
        var pick = new WeightPickedEventArguments();
        pick.weightsId = 1;
        pick.label = "weights(2 entries) @ main.ql:3";
        pick.kind = "pick";
        pick.song = "\"" + trackId + "\"";
        pick.trackId = trackId;
        pick.path = new ArrayList<>();
        for (var index : indices) {
            var level = new WeightPickedEventArguments.Level();
            level.index = index;
            level.name = "[" + index + "]";
            pick.path.add(level);
        }
        pick.breakdown = breakdown;
        return pick;
    }

    /** {@code [|~30% [inner, 100 - inner], 70% collection}} */
    private static WeightPickedEventArguments.Breakdown breakdown(double first, double second) {
        var a = node("[0] |50%", 0, "song", first, 30 * first / 100, List.of());
        var b = node("[1] |50%", 1, "song", second, 30 * second / 100, List.of());
        var breakdown = new WeightPickedEventArguments.Breakdown();
        breakdown.roots = List.of(node("[0] |~30%", 0, "weights", 30, 30, List.of(a, b)), node("[1] |70%", 1, "collection", 70, 70, List.of()));
        return breakdown;
    }

    private static WeightPickedEventArguments.Node node(String name, int index, String kind, double chance, double combined, List<WeightPickedEventArguments.Node> children) {
        var node = new WeightPickedEventArguments.Node();
        node.name = name;
        node.index = index;
        node.kind = kind;
        node.chance = chance;
        node.combined = combined;
        node.children = children;
        return node;
    }
}
