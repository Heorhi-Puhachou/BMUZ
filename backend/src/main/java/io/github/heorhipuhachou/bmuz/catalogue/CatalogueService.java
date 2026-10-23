package io.github.heorhipuhachou.bmuz.catalogue;

import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.AlbumSummary;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.AlbumView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.ArtistSummary;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.ArtistTile;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.ArtistView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.CreditView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.LinkView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.PageView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.SearchResult;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.TrackHit;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.TrackView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CatalogueService {

    private static final int SEARCH_LIMIT = 20;

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final TrackRepository tracks;
    private final JdbcClient jdbc;

    public CatalogueService(ArtistRepository artists, AlbumRepository albums, TrackRepository tracks,
                            JdbcClient jdbc) {
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
        this.jdbc = jdbc;
    }

    public PageView<ArtistTile> listArtists(int page, int size, boolean freeOnly) {
        PageRequest request = PageRequest.of(page, size, Sort.by("name"));
        Page<Artist> result = freeOnly
                ? artists.findWithFreeAlbum(true, FreeFilter.FREE_TYPES, request)
                : artists.findAll(request);
        List<Long> ids = result.getContent().stream().map(Artist::getId).toList();
        Map<Long, Album> newest = new HashMap<>();
        for (Object[] row : albums.findNewestFirstByArtists(ids, freeOnly, FreeFilter.FREE_TYPES)) {
            newest.putIfAbsent((Long) row[0], (Album) row[1]);
        }
        List<ArtistTile> tiles = result.getContent().stream()
                .map(a -> new ArtistTile(a.getId(), a.getName(), coverUrl(newest.get(a.getId()))))
                .toList();
        return new PageView<>(tiles, page, size, result.getTotalElements());
    }

    public ArtistView artist(long id, boolean freeOnly) {
        Artist artist = artists.findById(id).orElseThrow(() -> notFound("artist", id));
        List<Album> own = albums.findByArtist(id, freeOnly, FreeFilter.FREE_TYPES);
        Set<Long> ownIds = own.stream().map(Album::getId).collect(Collectors.toSet());
        List<TrackHit> otherTracks = tracks.findCreditedToArtist(id, freeOnly, FreeFilter.FREE_TYPES).stream()
                .filter(t -> !ownIds.contains(t.getAlbum().getId()))
                .map(t -> new TrackHit(t.getId(), t.getNumber(), t.getTitle(), credit(t.getCredits()),
                        summary(t.getAlbum())))
                .toList();
        return new ArtistView(artist.getId(), artist.getName(),
                artist.getAliases().stream().map(ArtistAlias::getName).toList(),
                own.stream().map(CatalogueService::summary).toList(),
                otherTracks);
    }

    public AlbumView album(long id) {
        Album album = albums.findById(id).orElseThrow(() -> notFound("album", id));
        List<TrackView> trackViews = album.getTracks().stream()
                .map(t -> new TrackView(t.getId(), t.getNumber(), t.getTitle(), t.getLengthSeconds(),
                        credit(t.getCredits()), t.getLinks().stream().map(CatalogueService::view).toList()))
                .toList();
        return new AlbumView(album.getId(), album.getYear(), album.getTitle(), coverUrl(album),
                credit(album.getCredits()), trackViews,
                album.getLinks().stream().map(CatalogueService::view).toList());
    }

    public SearchResult search(String query, boolean freeOnly) {
        String q = query.strip();
        if (q.isEmpty()) {
            return new SearchResult(List.of(), List.of());
        }
        List<ArtistSummary> foundArtists = jdbc.sql("""
                        SELECT a.id, a.name
                        FROM artist a
                        LEFT JOIN artist_alias al ON al.artist_id = a.id
                        WHERE (a.name ILIKE '%' || :q || '%'
                           OR al.name ILIKE '%' || :q || '%'
                           OR :q <% a.name
                           OR :q <% al.name)
                          AND (:freeOnly = false OR EXISTS (
                              SELECT 1 FROM album_credit ac WHERE ac.artist_id = a.id AND
                        """ + FreeFilter.sql("ac.album_id") + """
                          ))
                        GROUP BY a.id, a.name
                        ORDER BY max(greatest(word_similarity(:q, a.name),
                                              coalesce(word_similarity(:q, al.name), 0))) DESC, a.name
                        LIMIT :limit
                        """)
                .param("q", q)
                .param("freeOnly", freeOnly)
                .param("limit", SEARCH_LIMIT)
                .query((rs, n) -> new ArtistSummary(rs.getLong("id"), rs.getString("name")))
                .list();
        List<Long> albumIds = jdbc.sql("""
                        SELECT id
                        FROM album
                        WHERE (title ILIKE '%' || :q || '%'
                           OR :q <% title)
                          AND
                        """ + FreeFilter.sql("album.id") + """
                        ORDER BY word_similarity(:q, title) DESC, title
                        LIMIT :limit
                        """)
                .param("q", q)
                .param("freeOnly", freeOnly)
                .param("limit", SEARCH_LIMIT)
                .query(Long.class)
                .list();
        Map<Long, Album> found = albums.findAllById(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));
        List<AlbumSummary> foundAlbums = albumIds.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .map(CatalogueService::summary)
                .toList();
        return new SearchResult(foundArtists, foundAlbums);
    }

    private static AlbumSummary summary(Album album) {
        return new AlbumSummary(album.getId(), album.getYear(), album.getTitle(), coverUrl(album),
                credit(album.getCredits()));
    }

    private static List<CreditView> credit(List<Credit> credits) {
        return credits.stream()
                .map(c -> new CreditView(c.getArtist().getId(), c.getArtist().getName(), c.getJoinPhrase()))
                .toList();
    }

    /** Вокладак пакуль няма: яны зьявяцца разам з табліцай {@code cover} (крок 6 пляну). */
    private static String coverUrl(Album album) {
        return null;
    }

    private static LinkView view(Link link) {
        return new LinkView(link.getStore().getName(), link.getUrl(), link.getPriceType(), link.getMinPrice(),
                link.getCurrency(), link.getNote());
    }

    private static ResponseStatusException notFound(String what, long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " " + id + " not found");
    }
}
