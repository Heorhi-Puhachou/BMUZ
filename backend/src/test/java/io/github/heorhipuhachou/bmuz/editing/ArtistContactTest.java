package io.github.heorhipuhachou.bmuz.editing;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.account.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "bmuz.covers.local-dir=target/test-covers")
@AutoConfigureMockMvc
class ArtistContactTest {

    private static final String ADMIN = "admin@example.org";
    private static final String BAND = "band@example.org";
    private static final String OTHER_BAND = "other@example.org";
    private static final String PASSWORD = "a long enough password";
    private static final String CONTACTS = """
            {"contacts":[
              {"kind":"EMAIL","value":"booking@example.org"},
              {"kind":"WEBSITE","value":"https://band.example"},
              {"kind":"BANDCAMP","value":"https://band.bandcamp.com"},
              {"kind":"INSTAGRAM","value":"https://www.instagram.com/band"}]}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private long artist;
    private long otherArtist;

    @BeforeEach
    void artistsAndAccounts(@Autowired AppUserRepository users, @Autowired PasswordEncoder encoder) {
        jdbc.sql("TRUNCATE artist, album, app_user, edit_log RESTART IDENTITY CASCADE").update();
        for (String email : new String[]{ADMIN, BAND, OTHER_BAND}) {
            users.save(new AppUser(email, encoder.encode(PASSWORD), Role.ADMIN));
        }
        jdbc.sql("UPDATE app_user SET role = 'ARTIST' WHERE email <> ?").param(ADMIN).update();
        artist = id("INSERT INTO artist (name) VALUES ('Band') RETURNING id");
        otherArtist = id("INSERT INTO artist (name) VALUES ('Other') RETURNING id");
        member(artist, BAND);
        member(otherArtist, OTHER_BAND);
    }

    private long id(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private void member(long artistId, String email) {
        jdbc.sql("INSERT INTO artist_member (artist_id, user_id) SELECT ?, id FROM app_user WHERE email = ?")
                .params(artistId, email).update();
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private int call(HttpMethod method, String path, String json, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = request(method, path).with(csrf()).session(session);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private int save(MockHttpSession session, long artistId, String json) throws Exception {
        return call(HttpMethod.PUT, "/api/edit/artists/" + artistId + "/contacts", json, session);
    }

    private static String one(String kind, String value) {
        return "{\"contacts\":[{\"kind\":\"" + kind + "\",\"value\":\"" + value + "\"}]}";
    }

    @Test
    void theArtistPublishesItsContactsInOrder() throws Exception {
        assertThat(save(login(BAND), artist, CONTACTS)).isEqualTo(204);
        mvc.perform(get("/api/artists/" + artist))
                .andExpect(jsonPath("$.contacts.length()").value(4))
                .andExpect(jsonPath("$.contacts[0].kind").value("EMAIL"))
                .andExpect(jsonPath("$.contacts[3].value").value("https://www.instagram.com/band"));
        mvc.perform(get("/api/artists/" + otherArtist)).andExpect(jsonPath("$.contacts").isEmpty());
        assertThat(id("SELECT count(*) FROM edit_log WHERE action = 'CONTACTS_UPDATE'")).isEqualTo(1);

        assertThat(save(login(BAND), artist, "{\"contacts\":[]}")).as("all taken off").isEqualTo(204);
        mvc.perform(get("/api/artists/" + artist)).andExpect(jsonPath("$.contacts").isEmpty());
    }

    @Test
    void onlyTheArtistsOwnAccountWritesThem() throws Exception {
        assertThat(save(login(ADMIN), artist, CONTACTS)).as("an admin").isEqualTo(403);
        assertThat(save(login(OTHER_BAND), artist, CONTACTS)).as("another artist").isEqualTo(403);
        assertThat(id("SELECT count(*) FROM artist_contact")).isZero();
    }

    @Test
    void anAdminRemovesButAnArtistCannotUseThatRoute() throws Exception {
        save(login(BAND), artist, CONTACTS);
        long contact = id("SELECT id FROM artist_contact WHERE kind = 'WEBSITE'");
        assertThat(call(HttpMethod.DELETE, "/api/admin/artist-contacts/" + contact, null, login(BAND)))
                .isEqualTo(403);
        assertThat(call(HttpMethod.DELETE, "/api/admin/artist-contacts/" + contact, null, login(ADMIN)))
                .isEqualTo(204);
        mvc.perform(get("/api/artists/" + artist)).andExpect(jsonPath("$.contacts.length()").value(3));
        assertThat(id("SELECT count(*) FROM edit_log WHERE action = 'CONTACT_REMOVE'")).isEqualTo(1);
    }

    @Test
    void eachKindChecksItsAddress() throws Exception {
        MockHttpSession band = login(BAND);
        assertThat(save(band, artist, one("EMAIL", "not an address"))).isEqualTo(400);
        assertThat(save(band, artist, one("WEBSITE", "band.example"))).as("no https://").isEqualTo(400);
        assertThat(save(band, artist, one("INSTAGRAM", "https://evil.example/instagram.com"))).isEqualTo(400);
        assertThat(save(band, artist, one("INSTAGRAM", "https://notinstagram.com/band"))).isEqualTo(400);
        assertThat(save(band, artist, one("YOUTUBE", "https://youtu.be/abc"))).isEqualTo(204);
        assertThat(save(band, artist, one("SOUNDCLOUD", "https://m.soundcloud.com/band"))).isEqualTo(204);
        assertThat(save(band, artist, one("OTHER", "https://x.example/" + "a".repeat(300)))).as("too long")
                .isEqualTo(400);
        assertThat(save(band, artist, "{\"contacts\":[{\"value\":\"https://band.example\"}]}")).as("no kind")
                .isEqualTo(400);
    }
}
