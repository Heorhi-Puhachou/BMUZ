package io.github.heorhipuhachou.bmuz.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Хто працуе з сайтам: адміністратары і ўліковыя запісы выканаўцаў. Запрашае толькі суперадміністратар — гэта
 * правіла ў SecurityConfig. Спасылка з ліста стварае ўліковы запіс або дае правы ўжо існаму; існы запіс спачатку
 * ўваходзіць, каб запрашэньне не прыняў хтосьці іншы ад ягонага імені.
 */
@Service
public class TeamService {

    static final String INVITE_DAYS = "7";

    public record Member(long userId, String email, String role, Instant joinedAt) {
    }

    public record PendingInvite(String email, Instant expiresAt) {
    }

    /** Хто рэдагуе выканаўцу, або хто адміністратар, — і каго запрасілі. */
    public record Team(List<Member> members, List<PendingInvite> invites) {
    }

    /** artistId і artistName — толькі ў запрашэньня выканаўцам. needsPassword — у адраса яшчэ няма запісу. */
    public record InviteInfo(Role role, Long artistId, String artistName, String email, boolean needsPassword) {
    }

    private record Invite(long id, Role role, Long artistId, String artistName, String email) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final JdbcClient jdbc;
    private final String siteUrl;
    private final SecureRandom random = new SecureRandom();

    TeamService(AppUserRepository users, PasswordEncoder encoder, Mailer mailer, JdbcClient jdbc,
                @Value("${bmuz.site-url:http://localhost:5173}") String siteUrl) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
        this.jdbc = jdbc;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    /** Выканаўцы, якіх рэдагуе гэты адрас. */
    @Transactional(readOnly = true)
    public Set<Long> managedArtists(String email) {
        return new HashSet<>(jdbc.sql("""
                        SELECT m.artist_id FROM artist_member m JOIN app_user u ON u.id = m.user_id
                        WHERE u.email = ?""")
                .param(AppUser.normalise(email)).query(Long.class).list());
    }

    /** Новае запрашэньне замяняе ранейшае на той самы адрас і выканаўцу. */
    @Transactional
    public void inviteArtist(long artistId, String email, String inviterEmail) {
        String artistName = jdbc.sql("SELECT name FROM artist WHERE id = ?").param(artistId)
                .query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artist not found"));
        String normalised = address(email);
        jdbc.sql("DELETE FROM invite WHERE role = 'ARTIST' AND artist_id = ? AND email = ?")
                .params(artistId, normalised).update();
        String link = invite(Role.ARTIST, artistId, normalised, inviterEmail);
        mailer.send(normalised, "BMUZ: запрашэньне",
                "Вас запрасілі рэдагаваць старонку «" + artistName + "» на " + siteUrl + ".\n"
                        + "Адкрыйце спасылку на працягу " + INVITE_DAYS + " дзён:\n" + link + "\n\n"
                        + "Калі вы не чакалі гэтага ліста, нічога не рабіце.");
    }

    @Transactional
    public void inviteAdmin(String email, String inviterEmail) {
        String normalised = address(email);
        boolean admin = users.findByEmail(normalised).map(u -> u.getRole().isAdmin()).orElse(false);
        if (admin) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, normalised + " is already an admin");
        }
        jdbc.sql("DELETE FROM invite WHERE role = 'ADMIN' AND email = ?").param(normalised).update();
        String link = invite(Role.ADMIN, null, normalised, inviterEmail);
        mailer.send(normalised, "BMUZ: запрашэньне",
                "Вас запрасілі адміністратарам сайта " + siteUrl + ".\n"
                        + "Адкрыйце спасылку на працягу " + INVITE_DAYS + " дзён:\n" + link + "\n\n"
                        + "Калі вы не чакалі гэтага ліста, нічога не рабіце.");
    }

    @Transactional(readOnly = true)
    public Team artistTeam(long artistId) {
        List<Member> members = jdbc.sql("""
                        SELECT u.id, u.email, u.role, m.joined_at FROM artist_member m JOIN app_user u ON u.id = m.user_id
                        WHERE m.artist_id = ? ORDER BY u.email""")
                .param(artistId)
                .query((rs, n) -> new Member(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant()))
                .list();
        return new Team(members, pending("role = 'ARTIST' AND artist_id = ?", artistId));
    }

    @Transactional(readOnly = true)
    public Team admins() {
        List<Member> members = jdbc.sql("""
                        SELECT id, email, role, created_at FROM app_user
                        WHERE role IN ('SUPER_ADMIN', 'ADMIN') ORDER BY role DESC, email""")
                .query((rs, n) -> new Member(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant()))
                .list();
        return new Team(members, pending("role = 'ADMIN'"));
    }

    @Transactional
    public void removeMember(long artistId, long userId) {
        jdbc.sql("DELETE FROM artist_member WHERE artist_id = ? AND user_id = ?").params(artistId, userId).update();
        if (!managesAny(userId)) {
            users.findById(userId).ifPresent(AppUser::becomeUser);
        }
    }

    /** Адміністратар застаецца выканаўцам, калі рэдагуе выканаўцаў, інакш — звычайным карыстальнікам. */
    @Transactional
    public void removeAdmin(long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such user"));
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "the super-admin is changed only by the command on the server");
        }
        if (user.getRole() == Role.ADMIN) {
            user.setRole(managesAny(userId) ? Role.ARTIST : Role.USER);
        }
    }

    @Transactional
    public void cancelArtistInvite(long artistId, String email) {
        jdbc.sql("DELETE FROM invite WHERE role = 'ARTIST' AND artist_id = ? AND email = ?")
                .params(artistId, AppUser.normalise(email == null ? "" : email)).update();
    }

    @Transactional
    public void cancelAdminInvite(String email) {
        jdbc.sql("DELETE FROM invite WHERE role = 'ADMIN' AND email = ?")
                .param(AppUser.normalise(email == null ? "" : email)).update();
    }

    @Transactional(readOnly = true)
    public Optional<InviteInfo> inviteInfo(String token) {
        return find(token).map(i -> new InviteInfo(i.role(), i.artistId(), i.artistName(), i.email(),
                users.findByEmail(i.email()).filter(AppUser::isConfirmed).isEmpty()));
    }

    /**
     * Прымае запрашэньне. {@code false} — спасылка няправільная, ужо выкарыстаная або састарэла.
     * {@code currentEmail} — хто зараз увайшоў, або null.
     */
    @Transactional
    public boolean accept(String token, String password, String currentEmail) {
        Invite invite = find(token).orElse(null);
        if (invite == null) {
            return false;
        }
        AppUser user = users.findByEmail(invite.email()).orElse(null);
        if (user != null && user.isConfirmed()) {
            if (currentEmail == null || !AppUser.normalise(currentEmail).equals(invite.email())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "log in as " + invite.email() + " first");
            }
        } else {
            if (password == null || password.length() < RegistrationService.MIN_PASSWORD_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "the password needs at least " + RegistrationService.MIN_PASSWORD_LENGTH + " characters");
            }
            // Непацьверджаная рэгістрацыя на гэты адрас саступае месца: спасылка з ліста ўжо пацьвердзіла адрас.
            if (user != null) {
                users.delete(user);
                users.flush();
            }
            String hash = encoder.encode(password);
            user = users.save(invite.role() == Role.ADMIN
                    ? new AppUser(invite.email(), hash, Role.ADMIN)
                    : AppUser.artist(invite.email(), hash));
        }
        if (invite.role() == Role.ADMIN) {
            user.becomeAdmin();
        } else {
            user.becomeArtist();
            jdbc.sql("""
                            INSERT INTO artist_member (artist_id, user_id, invited_by)
                            SELECT artist_id, ?, invited_by FROM invite WHERE id = ?
                            ON CONFLICT DO NOTHING""")
                    .params(user.getId(), invite.id()).update();
        }
        jdbc.sql("DELETE FROM invite WHERE id = ?").param(invite.id()).update();
        return true;
    }

    private String invite(Role role, Long artistId, String email, String inviterEmail) {
        String token = newToken();
        jdbc.sql("""
                        INSERT INTO invite (role, artist_id, email, token_hash, expires_at, invited_by)
                        VALUES (?, ?, ?, ?, now() + interval '%s days', (SELECT id FROM app_user WHERE email = ?))"""
                        .formatted(INVITE_DAYS))
                .params(role.name(), artistId, email, RegistrationService.hash(token),
                        AppUser.normalise(inviterEmail))
                .update();
        return siteUrl + "/invite?token=" + token;
    }

    private List<PendingInvite> pending(String where, Object... params) {
        return jdbc.sql("SELECT email, expires_at FROM invite WHERE " + where + " AND expires_at > now() ORDER BY email")
                .params(params)
                .query((rs, n) -> new PendingInvite(rs.getString(1), rs.getTimestamp(2).toInstant()))
                .list();
    }

    private boolean managesAny(long userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM artist_member WHERE user_id = ?)")
                .param(userId).query(Boolean.class).single();
    }

    private Optional<Invite> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT i.id, i.role, i.artist_id, a.name, i.email
                        FROM invite i LEFT JOIN artist a ON a.id = i.artist_id
                        WHERE i.token_hash = ? AND i.expires_at > now()""")
                .param(RegistrationService.hash(token))
                .query((rs, n) -> new Invite(rs.getLong(1), Role.valueOf(rs.getString(2)),
                        rs.getObject(3, Long.class), rs.getString(4), rs.getString(5)))
                .optional();
    }

    private static String address(String email) {
        String normalised = AppUser.normalise(email == null ? "" : email);
        if (!normalised.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not an email address");
        }
        return normalised;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
