package io.github.heorhipuhachou.bmuz.cover;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.catalogue.AlbumRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Вокладкі альбомаў і выявы выканаўцаў. Падставы для іх сайт не захоўвае: доказы ўладальнік трымае ў сваёй пошце. Для
 * адкрытай ліцэнзіі захоўваюцца толькі яе назва і старонка, дзе яна паказаная.
 */
@Service
public class CoverService {

    /** Для адкрытай ліцэнзіі — назва ліцэнзіі і крыніца: ліцэнзіі тыпу CC BY патрабуюць паказваць іх побач з выявай. */
    public record CoverUrls(String large, String small, String licence, String source) {
    }

    private final CoverRepository covers;
    private final AlbumRepository albums;
    private final AppUserRepository users;
    private final CoverStorage storage;
    private final JdbcClient jdbc;

    public CoverService(CoverRepository covers, AlbumRepository albums, AppUserRepository users, CoverStorage storage,
                        JdbcClient jdbc) {
        this.covers = covers;
        this.albums = albums;
        this.users = users;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<Long, CoverUrls> urls(Collection<Long> albumIds) {
        Map<Long, CoverUrls> result = new HashMap<>();
        for (Cover cover : covers.findAllById(albumIds)) {
            result.put(cover.getAlbumId(), urls(cover));
        }
        return result;
    }

    /** licence і licenceUrl — толькі для адкрытай ліцэнзіі, абодва разам; інакш абодва пустыя. */
    @Transactional
    public CoverUrls upload(long albumId, String licence, String licenceUrl, byte[] file, String email)
            throws IOException {
        if (!albums.existsById(albumId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "album not found");
        }
        licence = blankToNull(licence);
        licenceUrl = blankToNull(licenceUrl);
        if ((licence == null) != (licenceUrl == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "an open licence needs both its name and the page showing it");
        }
        if (licenceUrl != null && !licenceUrl.matches("(?i)https?://\\S+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the licence page must be an http(s) address");
        }

        CoverImages.Resized resized = CoverImages.resize(file);
        String base = "covers/" + albumId + "-" + UUID.randomUUID();
        String largeKey = base + "-" + CoverImages.LARGE + ".jpg";
        String smallKey = base + "-" + CoverImages.SMALL + ".jpg";
        storage.put(largeKey, resized.large());
        storage.put(smallKey, resized.small());

        Cover old = covers.findById(albumId).orElse(null);
        if (old != null) {
            covers.delete(old);
            covers.flush();
        }
        Cover saved = covers.save(new Cover(albumId, largeKey, smallKey, licence, licenceUrl, user(email)));
        if (old != null) {
            storage.delete(old.getLargeKey());
            storage.delete(old.getSmallKey());
        }
        return urls(saved);
    }

    /** Выявы выканаўцаў, якія іх загрузілі, — малы памер для пліткі. Каго няма ў выніку, у таго выявы няма. */
    @Transactional(readOnly = true)
    public Map<Long, String> artistImages(Collection<Long> artistIds) {
        Map<Long, String> result = new HashMap<>();
        if (artistIds.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT artist_id, small_key FROM artist_image WHERE artist_id IN (:ids)")
                .param("ids", artistIds)
                .query((rs, n) -> Map.entry(rs.getLong(1), CoverStorage.url(rs.getString(2))))
                .list()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /** null, калі выканаўца выявы не загружаў. */
    @Transactional(readOnly = true)
    public CoverUrls artistImage(long artistId) {
        return jdbc.sql("SELECT large_key, small_key FROM artist_image WHERE artist_id = ?").param(artistId)
                .query((rs, n) -> new CoverUrls(CoverStorage.url(rs.getString(1)), CoverStorage.url(rs.getString(2)),
                        null, null))
                .optional().orElse(null);
    }

    /** Выява, якую выканаўца загружае сам. */
    @Transactional
    public CoverUrls uploadArtistImage(long artistId, byte[] file, String email) throws IOException {
        AppUser user = user(email);
        CoverImages.Resized resized = CoverImages.resize(file);
        String base = "covers/artist-" + artistId + "-" + UUID.randomUUID();
        String largeKey = base + "-" + CoverImages.LARGE + ".jpg";
        String smallKey = base + "-" + CoverImages.SMALL + ".jpg";
        storage.put(largeKey, resized.large());
        storage.put(smallKey, resized.small());
        List<String> old = oldKeys(artistId);
        jdbc.sql("""
                        INSERT INTO artist_image (artist_id, large_key, small_key, uploaded_by)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (artist_id) DO UPDATE SET large_key = excluded.large_key,
                            small_key = excluded.small_key, uploaded_by = excluded.uploaded_by, uploaded_at = now()""")
                .params(artistId, largeKey, smallKey, user.getId())
                .update();
        for (String key : old) {
            storage.delete(key);
        }
        return new CoverUrls(CoverStorage.url(largeKey), CoverStorage.url(smallKey), null, null);
    }

    @Transactional
    public void removeArtistImage(long artistId) throws IOException {
        List<String> old = oldKeys(artistId);
        jdbc.sql("DELETE FROM artist_image WHERE artist_id = ?").param(artistId).update();
        for (String key : old) {
            storage.delete(key);
        }
    }

    private List<String> oldKeys(long artistId) {
        return jdbc.sql("SELECT large_key, small_key FROM artist_image WHERE artist_id = ?").param(artistId)
                .query((rs, n) -> List.of(rs.getString(1), rs.getString(2)))
                .optional().orElse(List.of());
    }

    private static CoverUrls urls(Cover cover) {
        return new CoverUrls(CoverStorage.url(cover.getLargeKey()), CoverStorage.url(cover.getSmallKey()),
                cover.getLicence(), cover.getLicenceUrl());
    }

    @Transactional
    public void remove(long albumId) throws IOException {
        Cover cover = covers.findById(albumId).orElse(null);
        if (cover == null) {
            return;
        }
        covers.delete(cover);
        storage.delete(cover.getLargeKey());
        storage.delete(cover.getSmallKey());
    }

    private AppUser user(String email) {
        return users.findByEmail(AppUser.normalise(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "unknown account"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

}
