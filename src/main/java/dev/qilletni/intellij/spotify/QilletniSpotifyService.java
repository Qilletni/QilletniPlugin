package dev.qilletni.intellij.spotify;

import dev.qilletni.intellij.spotify.auth.SpotifyAuthService;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.*;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lightweight service wrapper around spotify-web-api-java for searching tracks, albums, and playlists.
 * Requests are authorized with the access token of {@link SpotifyAuthService}.
 */
public final class QilletniSpotifyService {
    private static final Executor EXEC = AppExecutorUtil.getAppExecutorService();

    public enum MusicType {
        SONG, ALBUM, COLLECTION;

        public static MusicType fromContext(MusicTypeContext ctx) {
            return switch (ctx) {
                case SONG, AMBIGUOUS -> SONG;
                case ALBUM -> ALBUM;
                case COLLECTION -> COLLECTION;
            };
        }
    }

    public enum MusicTypeContext { SONG, ALBUM, COLLECTION, AMBIGUOUS }

    public record MusicChoice(MusicType type, String id, String name, List<String> artists, String owner, Integer year, String imageUrl) {}

    private SpotifyApi api() {
        String token;
        try {
            token = SpotifyAuthService.getInstance().getFreshAccessToken().join();
        } catch (Exception e) {
            throw new RuntimeException("Not signed in to Spotify or token unavailable", e);
        }
        var builder = new SpotifyApi.Builder().setHost(URI.create("https://api.spotify.com").getHost());
        var api = builder.build();
        api.setAccessToken(token);
        return api;
    }

    public CompletableFuture<List<MusicChoice>> searchTracks(@NotNull String q, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = api().searchTracks(q).limit(limit).offset(offset).build().execute();
                return toSongChoice(asNonNullList(result.getItems()));
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                throw new RuntimeException(e);
            }
        }, EXEC);
    }

    public CompletableFuture<List<MusicChoice>> searchAlbums(@NotNull String q, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = api().searchAlbums(q).limit(limit).offset(offset).build().execute();
                return toAlbumChoice(asNonNullList(result.getItems()));
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                throw new RuntimeException(e);
            }
        }, EXEC);
    }

    public CompletableFuture<List<MusicChoice>> searchPlaylists(@NotNull String q, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = api().searchPlaylists(q).limit(limit).offset(offset).build().execute();
                return toPlaylistChoice(asNonNullList(result.getItems()));
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                throw new RuntimeException(e);
            }
        }, EXEC);
    }

    public CompletableFuture<List<MusicChoice>> searchAny(@NotNull String q, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = api().searchItem(q, "track,album,playlist").limit(limit).offset(offset).build().execute();
                var musicChoices = new ArrayList<MusicChoice>();

                musicChoices.addAll(toSongChoice(asNonNullList(result.getTracks().getItems())));
                musicChoices.addAll(toAlbumChoice(asNonNullList(result.getAlbums().getItems())));
                musicChoices.addAll(toPlaylistChoice(asNonNullList(result.getPlaylists().getItems())));

                return musicChoices;
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                throw new RuntimeException(e);
            }
        }, EXEC);
    }

    private static <T> List<T> asNonNullList(T[] array) {
        List<T> res = new ArrayList<>();
        for (T item : array) {
            if (item != null) res.add(item);
        }
        return res;
    }

    private static List<MusicChoice> toSongChoice(@NotNull List<Track> tracks) {
        List<MusicChoice> list = new ArrayList<>();
        for (Track t : tracks) {
            List<String> artists = new ArrayList<>();
            for (ArtistSimplified a : t.getArtists()) artists.add(a.getName());
            String img = firstImageUrl(t.getAlbum());
            Integer year = parseYear(t.getAlbum());
            list.add(new MusicChoice(MusicType.SONG, t.getId(), t.getName(), artists, null, year, img));
        }
        return list;
    }

    private static List<MusicChoice> toAlbumChoice(@NotNull List<AlbumSimplified> albums) {
        List<MusicChoice> list = new ArrayList<>();
        for (AlbumSimplified a : albums) {
            String img = firstImageUrl(a.getImages());
            Integer year = parseYear(a.getReleaseDate());
            List<String> artists = new ArrayList<>();
            for (ArtistSimplified as : a.getArtists()) artists.add(as.getName());
            list.add(new MusicChoice(MusicType.ALBUM, a.getId(), a.getName(), artists, null, year, img));
        }
        return list;
    }

    private static List<MusicChoice> toPlaylistChoice(@NotNull List<PlaylistSimplified> playlists) {
        List<MusicChoice> list = new ArrayList<>();
        for (PlaylistSimplified p : playlists) {
            String img = firstImageUrl(p.getImages());
            String owner = p.getOwner() != null ? p.getOwner().getDisplayName() : null;
            list.add(new MusicChoice(MusicType.COLLECTION, p.getId(), p.getName(), List.of(), owner, null, img));
        }
        return list;
    }

    private static String firstImageUrl(@Nullable AlbumSimplified album) {
        return album == null ? null : firstImageUrl(album.getImages());
    }

    private static String firstImageUrl(@Nullable Image[] images) {
        if (images == null || images.length == 0) return null;
        for (var img : images) {
            if (img != null && img.getUrl() != null) return img.getUrl();
        }
        return null;
    }

    private static Integer parseYear(@Nullable AlbumSimplified album) {
        return album == null ? null : parseYear(album.getReleaseDate());
    }

    private static Integer parseYear(@Nullable String releaseDate) {
        if (releaseDate == null) return null;
        // formats: YYYY or YYYY-MM-DD
        var s = releaseDate.trim();
        if (s.length() >= 4 && s.chars().limit(4).allMatch(Character::isDigit)) {
            try { return Integer.parseInt(s.substring(0, 4)); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
