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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "bmuz.covers.local-dir=target/test-covers")
@AutoConfigureMockMvc
class ArtistImageTest {

    private static final String ADMIN = "admin@example.org";
    private static final String BAND = "band@example.org";
    private static final String OTHER_BAND = "other@example.org";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private long artist;
    private MockMultipartFile file;

    @BeforeEach
    void artistAndAccounts(@Autowired AppUserRepository users, @Autowired PasswordEncoder encoder) throws Exception {
        jdbc.sql("TRUNCATE artist, album, app_user, edit_log, permission RESTART IDENTITY CASCADE").update();
        for (String email : new String[]{ADMIN, BAND, OTHER_BAND}) {
            users.save(new AppUser(email, encoder.encode(PASSWORD), Role.ADMIN));
        }
        jdbc.sql("UPDATE app_user SET role = 'ARTIST' WHERE email <> ?").param(ADMIN).update();
        artist = id("INSERT INTO artist (name) VALUES ('Band') RETURNING id");
        long other = id("INSERT INTO artist (name) VALUES ('Other') RETURNING id");
        member(artist, BAND);
        member(other, OTHER_BAND);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB), "png", png);
        file = new MockMultipartFile("file", "band.png", "image/png", png.toByteArray());
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

    private int upload(MockHttpSession session) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/edit/artists/" + artist + "/image").file(file)
                .with(csrf()).session(session)).andReturn().getResponse().getStatus();
    }

    private int remove(MockHttpSession session) throws Exception {
        return mvc.perform(request(HttpMethod.DELETE, "/api/edit/artists/" + artist + "/image").with(csrf())
                .session(session)).andReturn().getResponse().getStatus();
    }

    @Test
    void theArtistsOwnImageReplacesTheAlbumCoverOnItsTile() throws Exception {
        MockHttpSession band = login(BAND);
        assertThat(upload(band)).isEqualTo(200);
        assertThat(upload(band)).as("a second one replaces the first").isEqualTo(200);
        assertThat(id("SELECT count(*) FROM artist_image")).isEqualTo(1);
        assertThat(id("SELECT count(*) FROM permission")).as("one basis, reused").isEqualTo(1);
        mvc.perform(get("/api/artists"))
                .andExpect(jsonPath("$.items[0].name").value("Band"))
                .andExpect(jsonPath("$.items[0].coverUrl").value(startsWith("/covers/artist-" + artist + "-")));
        mvc.perform(get("/api/artists/" + artist)).andExpect(jsonPath("$.imageUrl").value(startsWith("/covers/")));
        assertThat(id("SELECT count(*) FROM edit_log WHERE action = 'ARTIST_IMAGE_UPLOAD'")).isEqualTo(2);

        assertThat(remove(band)).isEqualTo(204);
        mvc.perform(get("/api/artists/" + artist)).andExpect(jsonPath("$.imageUrl").value(nullValue()));
        mvc.perform(get("/api/artists")).andExpect(jsonPath("$.items[0].coverUrl").value(nullValue()));
    }

    @Test
    void anAdminRemovesButDoesNotUpload() throws Exception {
        assertThat(upload(login(ADMIN))).isEqualTo(403);
        assertThat(upload(login(OTHER_BAND))).as("another artist").isEqualTo(403);
        upload(login(BAND));
        assertThat(remove(login(OTHER_BAND))).isEqualTo(403);
        assertThat(remove(login(ADMIN))).isEqualTo(204);
        assertThat(id("SELECT count(*) FROM artist_image")).isZero();
        assertThat(id("SELECT count(*) FROM edit_log WHERE action = 'ARTIST_IMAGE_REMOVE'")).isEqualTo(1);
    }
}
