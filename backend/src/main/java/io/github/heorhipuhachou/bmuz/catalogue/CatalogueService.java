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
import io.github.heorhipuhachou.bmuz.cover.CoverService;
import io.github.heorhipuhachou.bmuz.cover.CoverService.CoverUrls;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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
    private final CoverService covers;
    private final JdbcClient jdbc;

    public CatalogueService(ArtistRepository artists, AlbumRepository albums, TrackRepository tracks,
                            CoverService covers, JdbcClient jdbc) {
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
        this.covers = covers;
        this.jdbc = jdbc;
    }

    public PageView<ArtistTile> listArtists(int page, int size, ListFilter filter) {
        PageRequest request = PageRequest.of(page, size, Sort.by("name"));
        Page<Artist> result = filtered(filter)
                ? artists.findWithFilteredAlbum(filter.freeOnly(), ListFilter.FREE_TYPES, filter.uncheckedBy(),
                        request)
                : artists.findAll(request);
        List<Long> ids = result.getContent().stream().map(Artist::getId).toList();
        Map<Long, Album> newest = new HashMap<>();
        for (Object[] row : albums.findNewestFirstByArtists(ids, filter.freeOnly(), ListFilter.FREE_TYPES,
                filter.uncheckedBy())) {
            newest.putIfAbsent((Long) row[0], (Album) row[1]);
        }
        Map<Long, CoverUrls> coverUrls = covers.urls(newest.values().stream().map(Album::getId).toList());
        List<ArtistTile> tiles = result.getContent().stream()
                .map(a -> new ArtistTile(a.getId(), a.getName(), small(newest.get(a.getId()), coverUrls)))
                .toList();
        return new PageView<>(tiles, page, size, result.getTotalElements());
    }

    public ArtistView artist(long id, ListFilter filter) {
        Artist artist = artists.findById(id).orElseThrow(() -> notFound("artist", id));
        List<Album> own = albums.findByArtist(id, filter.freeOnly(), ListFilter.FREE_TYPES,
                filter.uncheckedBy());
        Set<Long> ownIds = own.stream().map(Album::getId).collect(Collectors.toSet());
        List<Track> credited = tracks.findCreditedToArtist(id, filter.freeOnly(), ListFilter.FREE_TYPES,
                        filter.uncheckedBy()).stream()
                .filter(t -> !ownIds.contains(t.getAlbum().getId()))
                .toList();
        List<Long> shownAlbums = new ArrayList<>(ownIds);
        credited.forEach(t -> shownAlbums.add(t.getAlbum().getId()));
        Map<Long, CoverUrls> coverUrls = covers.urls(shownAlbums);
        List<TrackHit> otherTracks = credited.stream()
                .map(t -> new TrackHit(t.getId(), t.getNumber(), t.getTitle(), credit(t.getCredits()),
                        summary(t.getAlbum(), coverUrls)))
                .toList();
        return new ArtistView(artist.getId(), artist.getName(),
                artist.getAliases().stream().map(ArtistAlias::getName).toList(),
                own.stream().map(a -> summary(a, coverUrls)).toList(),
                otherTracks);
    }

    public AlbumView album(long id) {
        Album album = albums.findById(id).orElseThrow(() -> notFound("album", id));
        List<TrackView> trackViews = album.getTracks().stream()
                .map(t -> new TrackView(t.getId(), t.getNumber(), t.getTitle(), t.getLengthSeconds(),
                        credit(t.getCredits()), t.getLinks().stream().map(CatalogueService::view).toList()))
                .toList();
        CoverUrls cover = covers.urls(List.of(album.getId())).get(album.getId());
        return new AlbumView(album.getId(), album.getYear(), album.getTitle(),
                cover == null ? null : cover.large(),
                cover == null ? null : cover.licence(),
                cover == null ? null : cover.source(),
                credit(album.getCredits()), trackViews,
                album.getLinks().stream().map(CatalogueService::view).toList());
    }

    public SearchResult search(String query, ListFilter filter) {
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
                          AND (:filtered = false OR EXISTS (
                              SELECT 1 FROM album_credit ac WHERE ac.artist_id = a.id AND
                        """ + ListFilter.sql("ac.album_id") + """
                          ))
                        GROUP BY a.id, a.name
                        ORDER BY max(greatest(word_similarity(:q, a.name),
                                              coalesce(word_similarity(:q, al.name), 0))) DESC, a.name
                        LIMIT :limit
                        """)
                .param("q", q)
                .param("filtered", filtered(filter))
                .param("freeOnly", filter.freeOnly())
                .param("uncheckedBy", filter.uncheckedBy())
                .param("limit", SEARCH_LIMIT)
                .query((rs, n) -> new ArtistSummary(rs.getLong("id"), rs.getString("name")))
                .list();
        List<Long> albumIds = jdbc.sql("""
                        SELECT id
                        FROM album
                        WHERE (title ILIKE '%' || :q || '%'
                           OR :q <% title)
                          AND
                        """ + ListFilter.sql("album.id") + """
                        ORDER BY word_similarity(:q, title) DESC, title
                        LIMIT :limit
                        """)
                .param("q", q)
                .param("freeOnly", filter.freeOnly())
                .param("uncheckedBy", filter.uncheckedBy())
                .param("limit", SEARCH_LIMIT)
                .query(Long.class)
                .list();
        Map<Long, Album> found = albums.findAllById(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));
        Map<Long, CoverUrls> coverUrls = covers.urls(albumIds);
        List<AlbumSummary> foundAlbums = albumIds.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .map(a -> summary(a, coverUrls))
                .toList();
        return new SearchResult(foundArtists, foundAlbums);
    }

    private static boolean filtered(ListFilter filter) {
        return filter.freeOnly() || filter.uncheckedBy() != 0;
    }

    private static AlbumSummary summary(Album album, Map<Long, CoverUrls> coverUrls) {
        return new AlbumSummary(album.getId(), album.getYear(), album.getTitle(), small(album, coverUrls),
                credit(album.getCredits()));
    }

    private static String small(Album album, Map<Long, CoverUrls> coverUrls) {
        CoverUrls urls = album == null ? null : coverUrls.get(album.getId());
        return urls == null ? null : urls.small();
    }

    private static List<CreditView> credit(List<Credit> credits) {
        return credits.stream()
                .map(c -> new CreditView(c.getArtist().getId(), c.getArtist().getName(), c.getJoinPhrase()))
                .toList();
    }

    private static LinkView view(Link link) {
        return new LinkView(link.getId(), link.getStore().getName(), link.getUrl(), link.getPriceType(), link.getMinPrice(),
                link.getCurrency(), link.getNote());
    }

    private static ResponseStatusException notFound(String what, long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " " + id + " not found");
    }
}
