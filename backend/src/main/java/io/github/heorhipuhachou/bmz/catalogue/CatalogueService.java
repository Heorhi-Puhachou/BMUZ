package io.github.heorhipuhachou.bmz.catalogue;

import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.AlbumHit;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.AlbumSummary;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.AlbumView;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.ArtistSummary;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.ArtistView;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.LinkView;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.PageView;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.SearchResult;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.TrackView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogueService {

    private static final int SEARCH_LIMIT = 20;

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final JdbcClient jdbc;

    public CatalogueService(ArtistRepository artists, AlbumRepository albums, JdbcClient jdbc) {
        this.artists = artists;
        this.albums = albums;
        this.jdbc = jdbc;
    }

    public PageView<ArtistSummary> listArtists(int page, int size) {
        Page<Artist> result = artists.findAll(PageRequest.of(page, size, Sort.by("name")));
        return new PageView<>(result.map(CatalogueService::summary).getContent(), page, size,
                result.getTotalElements());
    }

    public ArtistView artist(long id) {
        Artist artist = artists.findById(id).orElseThrow(() -> notFound("artist", id));
        return new ArtistView(artist.getId(), artist.getName(),
                artist.getAliases().stream().map(ArtistAlias::getName).toList(),
                artist.getAlbums().stream().map(a -> new AlbumSummary(a.getId(), a.getYear(), a.getTitle())).toList());
    }

    public AlbumView album(long id) {
        Album album = albums.findById(id).orElseThrow(() -> notFound("album", id));
        List<TrackView> tracks = album.getTracks().stream()
                .map(t -> new TrackView(t.getId(), t.getNumber(), t.getTitle(), t.getLengthSeconds(),
                        t.getLinks().stream().map(CatalogueService::view).toList()))
                .toList();
        return new AlbumView(album.getId(), album.getYear(), album.getTitle(), summary(album.getArtist()), tracks,
                album.getLinks().stream().map(CatalogueService::view).toList());
    }

    public SearchResult search(String query) {
        String q = query.strip();
        if (q.isEmpty()) {
            return new SearchResult(List.of(), List.of());
        }
        List<ArtistSummary> foundArtists = jdbc.sql("""
                        SELECT a.id, a.name
                        FROM artist a
                        LEFT JOIN artist_alias al ON al.artist_id = a.id
                        WHERE a.name ILIKE '%' || :q || '%'
                           OR al.name ILIKE '%' || :q || '%'
                           OR :q <% a.name
                           OR :q <% al.name
                        GROUP BY a.id, a.name
                        ORDER BY max(greatest(word_similarity(:q, a.name),
                                              coalesce(word_similarity(:q, al.name), 0))) DESC, a.name
                        LIMIT :limit
                        """)
                .param("q", q)
                .param("limit", SEARCH_LIMIT)
                .query((rs, n) -> new ArtistSummary(rs.getLong("id"), rs.getString("name")))
                .list();
        List<AlbumHit> foundAlbums = jdbc.sql("""
                        SELECT al.id, al.year, al.title, a.id AS artist_id, a.name AS artist_name
                        FROM album al
                        JOIN artist a ON a.id = al.artist_id
                        WHERE al.title ILIKE '%' || :q || '%'
                           OR :q <% al.title
                        ORDER BY word_similarity(:q, al.title) DESC, al.title
                        LIMIT :limit
                        """)
                .param("q", q)
                .param("limit", SEARCH_LIMIT)
                .query((rs, n) -> new AlbumHit(rs.getLong("id"), rs.getInt("year"), rs.getString("title"),
                        new ArtistSummary(rs.getLong("artist_id"), rs.getString("artist_name"))))
                .list();
        return new SearchResult(foundArtists, foundAlbums);
    }

    private static ArtistSummary summary(Artist artist) {
        return new ArtistSummary(artist.getId(), artist.getName());
    }

    private static LinkView view(Link link) {
        return new LinkView(link.getStore().getName(), link.getUrl(), link.getPriceType(), link.getMinPrice(),
                link.getCurrency(), link.getNote());
    }

    private static ResponseStatusException notFound(String what, long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " " + id + " not found");
    }
}
