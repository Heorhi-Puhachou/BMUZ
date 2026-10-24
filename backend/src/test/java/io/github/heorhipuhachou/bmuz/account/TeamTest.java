package io.github.heorhipuhachou.bmuz.account;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmuz.account.RegistrationTest.RecordingMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Суперадміністратар адзін, і толькі ён запрашае адміністратараў і выканаўцаў і забірае іхныя правы. */
@Import({TestcontainersConfiguration.class, RegistrationTest.Mails.class})
@SpringBootTest(properties = "bmuz.site-url=https://bmuz.example")
@AutoConfigureMockMvc
class TeamTest {

    private static final String SUPER = "super@example.org";
    private static final String ADMIN = "admin@example.org";
    private static final String NEW_ADMIN = "new-admin@example.org";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailer mailer;

    @Autowired
    private JdbcClient jdbc;

    private MockHttpSession superAdmin;
    private long artistId;

    @BeforeEach
    void team(@Autowired AppUserRepository users, @Autowired PasswordEncoder encoder,
              @Autowired LoginLimiter limiter) throws Exception {
        jdbc.sql("TRUNCATE artist, album, store, app_user, edit_log RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(SUPER, encoder.encode(PASSWORD), Role.SUPER_ADMIN));
        users.save(new AppUser(ADMIN, encoder.encode(PASSWORD), Role.ADMIN));
        artistId = jdbc.sql("INSERT INTO artist (name) VALUES ('Band') RETURNING id").query(Long.class).single();
        mailer.sent.clear();
        limiter.clear();
        superAdmin = login(SUPER);
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private int call(HttpMethod method, String path, String json, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = request(method, path).with(csrf());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private String token() {
        Matcher m = Pattern.compile("invite\\?token=([A-Za-z0-9_-]+)").matcher(mailer.sent.getLast()[2]);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void invitesAnAdminWhoThenEditsEverythingButInvitesNobody() throws Exception {
        assertThat(call(HttpMethod.POST, "/api/super/admins/invites", "{\"email\":\"" + NEW_ADMIN + "\"}",
                superAdmin)).isEqualTo(202);
        assertThat(mailer.sent.getLast()[2]).contains("адміністратарам").contains("https://bmuz.example/invite?token=");
        String token = token();
        mvc.perform(get("/api/auth/invite").param("token", token))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.needsPassword").value(true));
        assertThat(call(HttpMethod.POST, "/api/auth/invite",
                "{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}", null)).isEqualTo(204);

        MockHttpSession admin = login(NEW_ADMIN);
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(call(HttpMethod.PUT, "/api/edit/artists/" + artistId, "{\"name\":\"Band\"}", admin)).isEqualTo(204);
        assertThat(call(HttpMethod.GET, "/api/admin/edits", null, admin)).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/super/admins", null, admin)).isEqualTo(403);
    }

    @Test
    void anOrdinaryAdminInvitesNobody() throws Exception {
        MockHttpSession admin = login(ADMIN);
        assertThat(call(HttpMethod.POST, "/api/super/admins/invites", "{\"email\":\"x@example.org\"}", admin))
                .isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/super/artists/" + artistId + "/invites",
                "{\"email\":\"x@example.org\"}", admin)).isEqualTo(403);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void anArtistAccountCanBecomeAnAdmin() throws Exception {
        call(HttpMethod.POST, "/api/super/artists/" + artistId + "/invites", "{\"email\":\"band@example.org\"}",
                superAdmin);
        call(HttpMethod.POST, "/api/auth/invite", "{\"token\":\"" + token() + "\",\"password\":\"" + PASSWORD + "\"}",
                null);
        MockHttpSession band = login("band@example.org");
        call(HttpMethod.POST, "/api/super/admins/invites", "{\"email\":\"band@example.org\"}", superAdmin);
        assertThat(call(HttpMethod.POST, "/api/auth/invite", "{\"token\":\"" + token() + "\"}", band)).isEqualTo(204);
        mvc.perform(get("/api/auth/me").session(band))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.artists[0]").value(artistId));
    }

    @Test
    void removingAnAdminTakesEffectOnTheirOpenSession() throws Exception {
        MockHttpSession admin = login(ADMIN);
        assertThat(call(HttpMethod.GET, "/api/admin/edits", null, admin)).isEqualTo(200);
        long id = jdbc.sql("SELECT id FROM app_user WHERE email = ?").param(ADMIN).query(Long.class).single();
        assertThat(call(HttpMethod.DELETE, "/api/super/admins/" + id, null, superAdmin)).isEqualTo(204);
        assertThat(call(HttpMethod.GET, "/api/admin/edits", null, admin)).as("same session").isEqualTo(403);
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void keepsTheSuperAdmin() throws Exception {
        long id = jdbc.sql("SELECT id FROM app_user WHERE email = ?").param(SUPER).query(Long.class).single();
        assertThat(call(HttpMethod.DELETE, "/api/super/admins/" + id, null, superAdmin)).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/api/super/admins/invites", "{\"email\":\"" + ADMIN + "\"}", superAdmin))
                .as("already an admin").isEqualTo(409);
    }

    @Test
    void theDatabaseAllowsOneSuperAdminOnly() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE app_user SET role = 'SUPER_ADMIN' WHERE email = ?").param(ADMIN)
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsAdminsAndPendingInvites() throws Exception {
        call(HttpMethod.POST, "/api/super/admins/invites", "{\"email\":\"" + NEW_ADMIN + "\"}", superAdmin);
        mvc.perform(get("/api/super/admins").session(superAdmin))
                .andExpect(jsonPath("$.members[0].role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.members[1].email").value(ADMIN))
                .andExpect(jsonPath("$.invites[0].email").value(NEW_ADMIN));
        assertThat(call(HttpMethod.DELETE, "/api/super/admins/invites?email=" + NEW_ADMIN, null, superAdmin))
                .isEqualTo(204);
        mvc.perform(get("/api/super/admins").session(superAdmin)).andExpect(jsonPath("$.invites").isEmpty());
    }
}
