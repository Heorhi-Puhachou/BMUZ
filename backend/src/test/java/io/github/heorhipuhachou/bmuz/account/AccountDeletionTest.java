package io.github.heorhipuhachou.bmuz.account;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmuz.account.RegistrationTest.RecordingMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Запіс выдаляецца з паролем; доказы дазволаў на вокладкі і журнал зьменаў застаюцца, адрас — не. */
@Import({TestcontainersConfiguration.class, RegistrationTest.Mails.class})
@SpringBootTest(properties = "bmuz.site-url=https://bmuz.example")
@AutoConfigureMockMvc
class AccountDeletionTest {

    private static final String ME = "band@example.org";
    private static final String SUPER = "super@example.org";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailer mailer;

    @Autowired
    private JdbcClient jdbc;

    private long me;

    @BeforeEach
    void accountWithACover(@Autowired AppUserRepository users, @Autowired PasswordEncoder encoder,
                           @Autowired LoginLimiter limiter) {
        jdbc.sql("TRUNCATE artist, album, app_user, edit_log RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(SUPER, encoder.encode(PASSWORD), Role.SUPER_ADMIN));
        me = users.save(new AppUser(ME, encoder.encode(PASSWORD), Role.ADMIN)).getId();
        jdbc.sql("UPDATE app_user SET role = 'ARTIST', display_name = 'Band' WHERE id = ?").param(me).update();
        long artist = id("INSERT INTO artist (name) VALUES ('Band') RETURNING id");
        long album = id("INSERT INTO album (year, title) VALUES (2020, 'A') RETURNING id");
        jdbc.sql("INSERT INTO album_credit (album_id, position, artist_id) VALUES (?, 0, ?)").params(album, artist)
                .update();
        jdbc.sql("INSERT INTO artist_member (artist_id, user_id) VALUES (?, ?)").params(artist, me).update();
        long basis = id("INSERT INTO permission (artist_id, kind, proof, granted_on, scope, created_by) VALUES ("
                + artist + ", 'WRITTEN_PERMISSION', 'Загружае сам выканаўца з уліковага запісу " + ME
                + "', current_date, 'covers', " + me + ") RETURNING id");
        jdbc.sql("INSERT INTO cover (album_id, large_key, small_key, permission_id, uploaded_by) VALUES (?, 'l', 's', ?, ?)")
                .params(album, basis, me).update();
        jdbc.sql("INSERT INTO edit_log (user_id, email, action, subject) VALUES (?, ?, 'ALBUM_UPDATE', 'A')")
                .params(me, ME).update();
        mailer.sent.clear();
        limiter.clear();
    }

    private long id(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private int delete(String password, MockHttpSession session) throws Exception {
        var request = post("/api/account/delete").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}");
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void deletesThePersonButKeepsTheCoverBasisAndTheLog() throws Exception {
        MockHttpSession phone = login(ME);
        MockHttpSession laptop = login(ME);
        assertThat(delete(PASSWORD, laptop)).isEqualTo(204);

        assertThat(id("SELECT count(*) FROM app_user WHERE email = '" + ME + "'")).isZero();
        assertThat(id("SELECT count(*) FROM artist_member")).isZero();
        assertThat(jdbc.sql("SELECT email FROM edit_log").query(String.class).single()).isEmpty();
        assertThat(id("SELECT count(*) FROM cover WHERE uploaded_by IS NULL")).isEqualTo(1);
        assertThat(id("SELECT count(*) FROM permission WHERE created_by IS NULL")).isEqualTo(1);
        assertThat(mailer.sent).hasSize(1);
        assertThat(mailer.sent.getFirst()[0]).isEqualTo(ME);

        mvc.perform(get("/api/auth/me").session(laptop)).andExpect(status().isNoContent());
        // Сэсія на іншай прыладзе таксама скончылася.
        mvc.perform(get("/api/auth/me").session(phone)).andExpect(status().isNoContent());
    }

    @Test
    void needsThePassword() throws Exception {
        assertThat(delete("wrong password", login(ME))).isEqualTo(403);
        assertThat(id("SELECT count(*) FROM app_user WHERE email = '" + ME + "'")).isEqualTo(1);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void theSuperAdminStays() throws Exception {
        assertThat(delete(PASSWORD, login(SUPER))).isEqualTo(409);
        assertThat(id("SELECT count(*) FROM app_user WHERE email = '" + SUPER + "'")).isEqualTo(1);
    }

    @Test
    void needsALogin() throws Exception {
        assertThat(delete(PASSWORD, null)).isEqualTo(401);
    }
}
