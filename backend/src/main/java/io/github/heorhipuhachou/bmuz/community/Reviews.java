package io.github.heorhipuhachou.bmuz.community;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.account.TeamService;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.Rating;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Водгукі на альбомы: ацэнка ад 1 да 10 і тэкст. Ацэнкі без тэксту няма, і пад кожным водгукам стаіць імя аўтара, таму
 * без імя ў профілі водгук не пішацца. Выканаўца не ацэньвае альбомы сваіх выканаўцаў. Пакуль bmuz.reviews.enabled
 * выключаны, водгукаў на сайце няма зусім: каментары не зьяўляюцца без тэксту прыватнасьці і шляху для скаргаў.
 */
@Service
public class Reviews {

    static final int MIN_TEXT = 10;
    static final int MAX_TEXT = 2000;
    /** Новых водгукаў ад аднаго запісу за суткі — не болей. Свой водгук мяняць можна колькі заўгодна. */
    static final int DAILY_LIMIT = 30;

    /** author — імя ў профілі аўтара; mine — гэта водгук таго, хто пытаецца. */
    public record ReviewView(long id, String author, int score, String text, Instant createdAt, Instant updatedAt,
                             boolean mine) {
    }

    /** rating — null, калі водгукаў няма. */
    public record AlbumReviews(Rating rating, List<ReviewView> reviews) {
    }

    public record ReviewInput(Integer score, String text) {
    }

    private final AppUserRepository users;
    private final TeamService team;
    private final JdbcClient jdbc;
    private final boolean enabled;

    Reviews(AppUserRepository users, TeamService team, JdbcClient jdbc,
            @Value("${bmuz.reviews.enabled:false}") boolean enabled) {
        this.users = users;
        this.team = team;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** email — null для наведніка. Найноўшыя першымі. */
    @Transactional(readOnly = true)
    public AlbumReviews of(long albumId, String email) {
        requireEnabled();
        requireAlbum(albumId);
        Long me = email == null ? null : users.findByEmail(AppUser.normalise(email)).map(AppUser::getId).orElse(null);
        List<ReviewView> reviews = jdbc.sql("""
                        SELECT r.id, u.display_name, r.score, r.text, r.created_at, r.updated_at, r.user_id
                        FROM review r JOIN app_user u ON u.id = r.user_id
                        WHERE r.album_id = ? ORDER BY r.created_at DESC, r.id DESC""")
                .param(albumId)
                .query((rs, n) -> new ReviewView(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant(),
                        me != null && me == rs.getLong(7)))
                .list();
        Rating rating = reviews.isEmpty() ? null
                : new Rating(reviews.stream().mapToInt(ReviewView::score).average().orElseThrow(), reviews.size());
        return new AlbumReviews(rating, reviews);
    }

    /** Новы водгук або зьмена свайго. */
    @Transactional
    public void save(long albumId, ReviewInput input, String email) {
        requireEnabled();
        requireAlbum(albumId);
        AppUser user = user(email);
        if (user.getDisplayName() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "set a display name on the account page first");
        }
        Set<Long> managed = team.managedArtists(user.getEmail());
        boolean own = jdbc.sql("SELECT artist_id FROM album_credit WHERE album_id = ?").param(albumId)
                .query(Long.class).list().stream().anyMatch(managed::contains);
        if (own) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "an artist does not review their own album");
        }
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM review WHERE album_id = ? AND user_id = ?)")
                .params(albumId, user.getId()).query(Boolean.class).single();
        if (!exists) {
            Instant from = user.reviewsFrom();
            if (from == null || Instant.now().isBefore(from)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "a new account reviews from " + from);
            }
            long today = jdbc.sql(
                            "SELECT count(*) FROM review WHERE user_id = ? AND created_at > now() - interval '1 day'")
                    .param(user.getId()).query(Long.class).single();
            if (today >= DAILY_LIMIT) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "at most " + DAILY_LIMIT + " new reviews a day");
            }
        }
        if (input.score() == null || input.score() < 1 || input.score() > 10) {
            throw bad("a score is a whole number from 1 to 10");
        }
        String text = input.text() == null ? "" : input.text().strip();
        int length = text.codePointCount(0, text.length());
        if (length < MIN_TEXT || length > MAX_TEXT) {
            throw bad("a review is " + MIN_TEXT + " to " + MAX_TEXT + " characters");
        }
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw bad("a review cannot contain control characters");
        }
        jdbc.sql("""
                        INSERT INTO review (album_id, user_id, score, text) VALUES (?, ?, ?, ?)
                        ON CONFLICT (album_id, user_id)
                        DO UPDATE SET score = excluded.score, text = excluded.text, updated_at = now()""")
                .params(albumId, user.getId(), input.score(), text)
                .update();
    }

    @Transactional
    public void deleteOwn(long albumId, String email) {
        requireEnabled();
        jdbc.sql("DELETE FROM review WHERE album_id = ? AND user_id = ?").params(albumId, user(email).getId())
                .update();
    }

    /** Адміністратар здымае чужы водгук. */
    @Transactional
    public void remove(long reviewId) {
        requireEnabled();
        if (jdbc.sql("DELETE FROM review WHERE id = ?").param(reviewId).update() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "review not found");
        }
    }

    private AppUser user(String email) {
        return users.findByEmail(AppUser.normalise(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private void requireAlbum(long albumId) {
        if (!jdbc.sql("SELECT EXISTS (SELECT 1 FROM album WHERE id = ?)").param(albumId).query(Boolean.class)
                .single()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "album not found");
        }
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
