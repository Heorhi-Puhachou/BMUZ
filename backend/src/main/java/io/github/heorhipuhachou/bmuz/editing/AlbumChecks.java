package io.github.heorhipuhachou.bmuz.editing;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Album;
import io.github.heorhipuhachou.bmuz.catalogue.AlbumRepository;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.CreditView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.PageView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Прагляд альбомаў адміністратарамі. Кожны адміністратар адзначае свае; альбом, зьменены пасьля прагляду, зноў
 * трапляе ў ягоны сьпіс не праглядзеных — з часам прагляду, каб было відаць, што гэта не новы альбом.
 */
@Service
@Transactional(readOnly = true)
public class AlbumChecks {

    /** checkedAt — null, калі гэты адміністратар альбом яшчэ не праглядаў. */
    public record Item(long id, int year, String title, List<CreditView> credit, Instant updatedAt,
                       Instant checkedAt) {
    }

    /** Прагляд аднаго адміністратара; changedSince — альбом зьмянілі пасьля яго. */
    public record Check(String email, Instant checkedAt, boolean changedSince) {
    }

    /** Альбомы, якіх гэты адміністратар яшчэ не праглядзеў. */
    public record Unchecked(List<Long> albumIds) {
    }

    private record Row(long id, Instant updatedAt, Instant checkedAt) {
    }

    private final AppUserRepository users;
    private final AlbumRepository albums;
    private final JdbcClient jdbc;

    AlbumChecks(AppUserRepository users, AlbumRepository albums, JdbcClient jdbc) {
        this.users = users;
        this.albums = albums;
        this.jdbc = jdbc;
    }

    /**
     * checked = false — не праглядзеныя: спачатку зьмененыя пасьля прагляду, потым ніколі не праглядзеныя; у абодвух
     * найсьвяжэйшыя зьмены першымі. checked = true — праглядзеныя й ня зьмененыя з таго часу, апошнія прагляды першымі.
     */
    public PageView<Item> list(String email, boolean checked, int page, int size) {
        long userId = userId(email);
        String where = checked ? "c.checked_at >= a.updated_at" : "c.album_id IS NULL OR a.updated_at > c.checked_at";
        String order = checked ? "c.checked_at DESC, a.id DESC"
                : "(c.album_id IS NOT NULL) DESC, a.updated_at DESC, a.id DESC";
        String from = " FROM album a LEFT JOIN album_check c ON c.album_id = a.id AND c.user_id = :user WHERE "
                + where;
        long total = jdbc.sql("SELECT count(*)" + from).param("user", userId).query(Long.class).single();
        List<Row> rows = jdbc.sql("SELECT a.id, a.updated_at, c.checked_at" + from + " ORDER BY " + order
                        + " LIMIT :limit OFFSET :offset")
                .param("user", userId)
                .param("limit", size)
                .param("offset", page * size)
                .query((rs, n) -> new Row(rs.getLong(1), instant(rs.getTimestamp(2)), instant(rs.getTimestamp(3))))
                .list();
        Map<Long, Album> found = albums.findAllById(rows.stream().map(Row::id).toList()).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));
        List<Item> items = rows.stream().filter(r -> found.containsKey(r.id())).map(r -> {
            Album album = found.get(r.id());
            return new Item(album.getId(), album.getYear(), album.getTitle(), credit(album), r.updatedAt(),
                    r.checkedAt());
        }).toList();
        return new PageView<>(items, page, size, total);
    }

    /** Толькі нумары — для адзнак на плітках, таму ўвесь сьпіс адразу. */
    public Unchecked unchecked(String email) {
        return new Unchecked(jdbc.sql("""
                        SELECT a.id FROM album a LEFT JOIN album_check c ON c.album_id = a.id AND c.user_id = ?
                        WHERE c.album_id IS NULL OR a.updated_at > c.checked_at ORDER BY a.id""")
                .param(userId(email)).query(Long.class).list());
    }

    /** Усе прагляды альбома — гэтага адміністратара й іншых, апошнія першымі. */
    public List<Check> checks(long albumId) {
        requireAlbum(albumId);
        return jdbc.sql("""
                        SELECT u.email, c.checked_at, a.updated_at > c.checked_at
                        FROM album_check c JOIN app_user u ON u.id = c.user_id JOIN album a ON a.id = c.album_id
                        WHERE c.album_id = ? ORDER BY c.checked_at DESC, u.email""")
                .param(albumId)
                .query((rs, n) -> new Check(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getBoolean(3)))
                .list();
    }

    /** Адзначае зноў, калі ўжо адзначаны: час прагляду становіцца цяперашнім. */
    @Transactional
    public void check(long albumId, String email) {
        long userId = userId(email);
        requireAlbum(albumId);
        jdbc.sql("""
                        INSERT INTO album_check (album_id, user_id) VALUES (?, ?)
                        ON CONFLICT (album_id, user_id) DO UPDATE SET checked_at = now()""")
                .params(albumId, userId)
                .update();
    }

    @Transactional
    public void uncheck(long albumId, String email) {
        long userId = userId(email);
        requireAlbum(albumId);
        jdbc.sql("DELETE FROM album_check WHERE album_id = ? AND user_id = ?").params(albumId, userId).update();
    }

    private long userId(String email) {
        return users.findByEmail(AppUser.normalise(email)).map(AppUser::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void requireAlbum(long albumId) {
        if (!albums.existsById(albumId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "album not found");
        }
    }

    private static List<CreditView> credit(Album album) {
        return album.getCredits().stream()
                .map(c -> new CreditView(c.getArtist().getId(), c.getArtist().getName(), c.getJoinPhrase()))
                .toList();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
