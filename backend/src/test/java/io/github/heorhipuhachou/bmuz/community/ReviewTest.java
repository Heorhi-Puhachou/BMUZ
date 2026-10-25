package io.github.heorhipuhachou.bmuz.community;

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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"bmuz.reviews.enabled=true", "bmuz.covers.local-dir=target/test-covers"})
@AutoConfigureMockMvc
class ReviewTest {

    private static final String ANNA = "anna@example.org";
    private static final String PETRA = "petra@example.org";
    private static final String NAMELESS = "nameless@example.org";
    private static final String BAND = "band@example.org";
    private static final String ADMIN = "admin@example.org";
    private static final String PASSWORD = "a long enough password";
    private static final String GOOD = "Найлепшы альбом году.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private long artist;
    private long album;

    @BeforeEach
    void albumAndPeople(@Autowired AppUserRepository users, @Autowired PasswordEncoder encoder) {
        jdbc.sql("TRUNCATE artist, album, app_user, edit_log RESTART IDENTITY CASCADE").update();
        for (String email : new String[]{ANNA, PETRA, NAMELESS, BAND, ADMIN}) {
            users.save(new AppUser(email, encoder.encode(PASSWORD), Role.ADMIN));
        }
        jdbc.sql("UPDATE app_user SET confirmed_at = now() - interval '2 days'").update();
        jdbc.sql("UPDATE app_user SET role = 'USER' WHERE email IN (?, ?, ?)").params(ANNA, PETRA, NAMELESS).update();
        jdbc.sql("UPDATE app_user SET role = 'ARTIST' WHERE email = ?").param(BAND).update();
        jdbc.sql("UPDATE app_user SET display_name = initcap(split_part(email, '@', 1)) WHERE email <> ?")
                .param(NAMELESS).update();
        artist = id("INSERT INTO artist (name) VALUES ('Band') RETURNING id");
        album = id("INSERT INTO album (year, title) VALUES (2020, 'A') RETURNING id");
        jdbc.sql("INSERT INTO album_credit (album_id, position, artist_id) VALUES (?, 0, ?)").params(album, artist)
                .update();
        jdbc.sql("INSERT INTO artist_member (artist_id, user_id) SELECT ?, id FROM app_user WHERE email = ?")
                .params(artist, BAND).update();
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

    private int review(MockHttpSession session, int score, String text) throws Exception {
        return review(session, album, score, text);
    }

    private int review(MockHttpSession session, long albumId, int score, String text) throws Exception {
        return call(HttpMethod.PUT, "/api/account/reviews/" + albumId,
                "{\"score\":" + score + ",\"text\":\"" + text + "\"}", session);
    }

    @Test
    void theAverageShowsOnTheAlbumAndTheArtist() throws Exception {
        MockHttpSession anna = login(ANNA);
        assertThat(review(anna, 8, GOOD)).isEqualTo(204);
        assertThat(review(login(PETRA), 9, "Добра, але задоўга.")).isEqualTo(204);

        mvc.perform(get("/api/albums/" + album + "/reviews").session(anna))
                .andExpect(jsonPath("$.rating.average").value(8.5))
                .andExpect(jsonPath("$.rating.count").value(2))
                .andExpect(jsonPath("$.reviews[0].author").value("Petra"))
                .andExpect(jsonPath("$.reviews[0].mine").value(false))
                .andExpect(jsonPath("$.reviews[1].author").value("Anna"))
                .andExpect(jsonPath("$.reviews[1].mine").value(true));
        mvc.perform(get("/api/albums/" + album + "/reviews")).andExpect(jsonPath("$.reviews[1].mine").value(false));
        mvc.perform(get("/api/artists")).andExpect(jsonPath("$.items[0].rating.average").value(8.5));
        mvc.perform(get("/api/artists/" + artist)).andExpect(jsonPath("$.albums[0].rating.count").value(2));
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.reviews").value(true));
    }

    @Test
    void oneReviewPerPersonThatTheyCanChangeOrDelete() throws Exception {
        MockHttpSession anna = login(ANNA);
        review(anna, 3, GOOD);
        assertThat(review(anna, 10, "Перадумала: геніяльна.")).isEqualTo(204);
        mvc.perform(get("/api/albums/" + album + "/reviews"))
                .andExpect(jsonPath("$.rating.count").value(1))
                .andExpect(jsonPath("$.reviews[0].score").value(10));

        assertThat(call(HttpMethod.DELETE, "/api/account/reviews/" + album, null, anna)).isEqualTo(204);
        mvc.perform(get("/api/albums/" + album + "/reviews"))
                .andExpect(jsonPath("$.rating").value(nullValue()))
                .andExpect(jsonPath("$.reviews").isEmpty());
        mvc.perform(get("/api/artists")).andExpect(jsonPath("$.items[0].rating").value(nullValue()));
    }

    @Test
    void aScoreNeedsWordsAndAName() throws Exception {
        MockHttpSession anna = login(ANNA);
        assertThat(review(anna, 8, "  123456789  ")).as("nine characters").isEqualTo(400);
        assertThat(review(anna, 8, "1234567890")).as("ten").isEqualTo(204);
        assertThat(review(anna, 0, GOOD)).isEqualTo(400);
        assertThat(review(anna, 11, GOOD)).isEqualTo(400);
        assertThat(review(anna, 8, "x".repeat(2001))).isEqualTo(400);
        assertThat(review(login(NAMELESS), 8, GOOD)).as("no display name").isEqualTo(409);
        assertThat(call(HttpMethod.PUT, "/api/account/display-name", "{\"displayName\":\"\"}", anna))
                .as("a reviewer keeps a name").isEqualTo(409);
        assertThat(review(null, 8, GOOD)).isEqualTo(401);
    }

    @Test
    void aNewAccountWaitsADayAfterConfirming() throws Exception {
        jdbc.sql("UPDATE app_user SET confirmed_at = now() - interval '23 hours' WHERE email = ?").param(ANNA).update();
        MockHttpSession anna = login(ANNA);
        assertThat(review(anna, 8, GOOD)).isEqualTo(403);
        mvc.perform(get("/api/auth/me").session(anna)).andExpect(jsonPath("$.reviewsFrom").isNotEmpty());

        jdbc.sql("UPDATE app_user SET confirmed_at = now() - interval '25 hours' WHERE email = ?").param(ANNA).update();
        assertThat(review(anna, 8, GOOD)).isEqualTo(204);
    }

    @Test
    void thirtyNewReviewsADay() throws Exception {
        MockHttpSession anna = login(ANNA);
        long first = 0;
        for (int i = 0; i < 30; i++) {
            long other = id("INSERT INTO album (year, title) VALUES (2021, 'B" + i + "') RETURNING id");
            first = first == 0 ? other : first;
            assertThat(review(anna, other, 7, GOOD)).isEqualTo(204);
        }
        assertThat(review(anna, 8, GOOD)).as("the thirty-first").isEqualTo(429);
        assertThat(review(anna, first, 9, "Перадумала: лепей.")).as("changing one's own").isEqualTo(204);

        jdbc.sql("UPDATE review SET created_at = now() - interval '25 hours'").update();
        assertThat(review(anna, 8, GOOD)).as("the next day").isEqualTo(204);
    }

    @Test
    void anArtistDoesNotReviewTheirOwnAlbum() throws Exception {
        assertThat(review(login(BAND), 10, GOOD)).isEqualTo(403);
    }

    @Test
    void anAdminRemovesAnyReview() throws Exception {
        review(login(ANNA), 1, "Дрэнна і нецікава.");
        long id = id("SELECT id FROM review");
        assertThat(call(HttpMethod.DELETE, "/api/admin/reviews/" + id, null, login(PETRA))).isEqualTo(403);
        assertThat(call(HttpMethod.DELETE, "/api/admin/reviews/" + id, null, login(ADMIN))).isEqualTo(204);
        assertThat(id("SELECT count(*) FROM review")).isZero();
    }

    @Test
    void reviewsGoWithTheAccount() throws Exception {
        review(login(ANNA), 7, GOOD);
        jdbc.sql("DELETE FROM app_user WHERE email = ?").param(ANNA).update();
        assertThat(id("SELECT count(*) FROM review")).isZero();
    }
}
