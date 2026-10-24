package io.github.heorhipuhachou.bmuz.editing;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.account.Mailer;
import io.github.heorhipuhachou.bmuz.account.Role;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, ArtistEditingTest.Mails.class})
@SpringBootTest(properties = {"bmuz.site-url=https://bmuz.example", "bmuz.covers.local-dir=target/test-covers"})
@AutoConfigureMockMvc
class ArtistEditingTest {

    private static final String ADMIN = "admin@example.org";
    private static final String ARTIST = "band@example.org";
    private static final String PASSWORD = "a long enough password";

    @TestConfiguration
    static class Mails {
        @Bean
        @Primary
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }

    static class RecordingMailer implements Mailer {
        final List<String[]> sent = new ArrayList<>();

        @Override
        public void send(String to, String subject, String text) {
            sent.add(new String[]{to, subject, text});
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailer mailer;

    @Autowired
    private JdbcClient jdbc;

    private MockHttpSession admin;
    private long band;
    private long stranger;
    private long bandAlbum;
    private long strangerAlbum;

    @BeforeEach
    void catalogueAndAdmin(@Autowired CatalogueImporter importer, @Autowired AppUserRepository users,
                           @Autowired PasswordEncoder encoder) throws Exception {
        jdbc.sql("TRUNCATE artist, album, store, app_user, edit_log RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(ADMIN, encoder.encode(PASSWORD), Role.SUPER_ADMIN));
        importer.merge(new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2020, "Band album", List.of(new CreditEntry("Band", null)), List.of(), List.of()),
                new AlbumEntry(2021, "Stranger album", List.of(new CreditEntry("Stranger", null)), List.of(),
                        List.of()))));
        band = id("SELECT id FROM artist WHERE name = 'Band'");
        stranger = id("SELECT id FROM artist WHERE name = 'Stranger'");
        bandAlbum = id("SELECT id FROM album WHERE title = 'Band album'");
        strangerAlbum = id("SELECT id FROM album WHERE title = 'Stranger album'");
        mailer.sent.clear();
        admin = login(ADMIN);
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

    private String token() {
        Matcher m = Pattern.compile("invite\\?token=([A-Za-z0-9_-]+)").matcher(mailer.sent.getLast()[2]);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    /** Адміністратар запрашае новы адрас, і той задае пароль па спасылцы. */
    private MockHttpSession newArtistAccount() throws Exception {
        assertThat(call(HttpMethod.POST, "/api/super/artists/" + band + "/invites",
                "{\"email\":\"" + ARTIST + "\"}", admin)).isEqualTo(202);
        assertThat(call(HttpMethod.POST, "/api/auth/invite",
                "{\"token\":\"" + token() + "\",\"password\":\"" + PASSWORD + "\"}", null)).isEqualTo(204);
        return login(ARTIST);
    }

    @Test
    void invitesANewAddressThatThenManagesItsArtist() throws Exception {
        call(HttpMethod.POST, "/api/super/artists/" + band + "/invites", "{\"email\":\"Band@Example.org\"}", admin);
        assertThat(mailer.sent).hasSize(1);
        assertThat(mailer.sent.getFirst()[0]).isEqualTo(ARTIST);
        assertThat(mailer.sent.getFirst()[2]).contains("«Band»").contains("https://bmuz.example/invite?token=");
        String token = token();

        mvc.perform(get("/api/auth/invite").param("token", token))
                .andExpect(jsonPath("$.artistName").value("Band"))
                .andExpect(jsonPath("$.needsPassword").value(true));
        assertThat(call(HttpMethod.POST, "/api/auth/invite", "{\"token\":\"" + token + "\",\"password\":\"short\"}",
                null)).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/api/auth/invite",
                "{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}", null)).isEqualTo(204);
        assertThat(call(HttpMethod.POST, "/api/auth/invite",
                "{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}", null)).as("used twice").isEqualTo(410);

        MockHttpSession artist = login(ARTIST);
        mvc.perform(get("/api/auth/me").session(artist))
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.artists[0]").value(band));
        mvc.perform(get("/api/super/artists/" + band + "/members").session(admin))
                .andExpect(jsonPath("$.members[0].email").value(ARTIST))
                .andExpect(jsonPath("$.invites").isEmpty());
    }

    @Test
    void anExistingAccountAcceptsOnlyWhenLoggedInAsThatAddress(@Autowired AppUserRepository users,
                                                               @Autowired PasswordEncoder encoder) throws Exception {
        users.save(new AppUser("fan@example.org", encoder.encode(PASSWORD), Role.ADMIN));
        jdbc.sql("UPDATE app_user SET role = 'USER' WHERE email = 'fan@example.org'").update();
        call(HttpMethod.POST, "/api/super/artists/" + band + "/invites", "{\"email\":\"fan@example.org\"}", admin);
        String token = token();
        mvc.perform(get("/api/auth/invite").param("token", token)).andExpect(jsonPath("$.needsPassword").value(false));

        assertThat(call(HttpMethod.POST, "/api/auth/invite", "{\"token\":\"" + token + "\"}", null)).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/api/auth/invite", "{\"token\":\"" + token + "\"}", admin))
                .as("someone else logged in").isEqualTo(401);
        MockHttpSession fan = login("fan@example.org");
        assertThat(call(HttpMethod.POST, "/api/auth/invite", "{\"token\":\"" + token + "\"}", fan)).isEqualTo(204);
        mvc.perform(get("/api/auth/me").session(login("fan@example.org")))
                .andExpect(jsonPath("$.role").value("ARTIST"));
    }

    @Test
    void refusesAnExpiredInvite() throws Exception {
        call(HttpMethod.POST, "/api/super/artists/" + band + "/invites", "{\"email\":\"" + ARTIST + "\"}", admin);
        jdbc.sql("UPDATE invite SET expires_at = now() - interval '1 minute'").update();
        assertThat(call(HttpMethod.POST, "/api/auth/invite",
                "{\"token\":\"" + token() + "\",\"password\":\"" + PASSWORD + "\"}", null)).isEqualTo(410);
    }

    @Test
    void anArtistCannotInvite() throws Exception {
        MockHttpSession artist = newArtistAccount();
        assertThat(call(HttpMethod.POST, "/api/super/artists/" + band + "/invites",
                "{\"email\":\"friend@example.org\"}", artist)).isEqualTo(403);
    }

    @Test
    void editsOwnArtistButNoOtherOne() throws Exception {
        MockHttpSession artist = newArtistAccount();
        assertThat(call(HttpMethod.PUT, "/api/edit/artists/" + band,
                "{\"name\":\"Band Renamed\",\"aliases\":[\"Бэнд\",\" \",\"Бэнд\"]}", artist)).isEqualTo(204);
        mvc.perform(get("/api/artists/" + band))
                .andExpect(jsonPath("$.name").value("Band Renamed"))
                .andExpect(jsonPath("$.aliases[0]").value("Бэнд"))
                .andExpect(jsonPath("$.aliases.length()").value(1));
        assertThat(call(HttpMethod.PUT, "/api/edit/artists/" + stranger, "{\"name\":\"Hacked\"}", artist))
                .isEqualTo(403);
        assertThat(call(HttpMethod.PUT, "/api/edit/artists/" + band, "{\"name\":\"Stranger\"}", artist))
                .as("a name another artist has").isEqualTo(409);
    }

    @Test
    void editsOwnAlbumsTracksAndLinks() throws Exception {
        MockHttpSession artist = newArtistAccount();
        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + bandAlbum,
                "{\"year\":2019,\"title\":\"Renamed\",\"credit\":[{\"artist\":\"Band\",\"join\":\" feat. \"},"
                        + "{\"artist\":\"Guest\"}]}", artist)).isEqualTo(204);
        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + bandAlbum + "/tracks",
                "{\"tracks\":[{\"number\":1,\"title\":\"One\",\"lengthSeconds\":200},{\"number\":2,\"title\":\"Two\"}]}",
                artist)).isEqualTo(204);
        assertThat(call(HttpMethod.POST, "/api/edit/albums/" + bandAlbum + "/links",
                "{\"store\":\"Bandcamp\",\"url\":\"https://band.bandcamp.com/album/renamed\",\"priceType\":\"NAME_YOUR_PRICE\"}",
                artist)).isEqualTo(201);
        assertThat(call(HttpMethod.POST, "/api/edit/albums/" + bandAlbum + "/links",
                "{\"store\":\"Own site\",\"url\":\"https://band.example/two.mp3\",\"priceType\":\"FREE\",\"trackNumber\":2}",
                artist)).isEqualTo(201);

        mvc.perform(get("/api/albums/" + bandAlbum))
                .andExpect(jsonPath("$.year").value(2019))
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.credit[1].name").value("Guest"))
                .andExpect(jsonPath("$.tracks[1].title").value("Two"))
                .andExpect(jsonPath("$.tracks[1].links[0].priceType").value("FREE"))
                .andExpect(jsonPath("$.links[0].priceType").value("NAME_YOUR_PRICE"));

        long linkId = id("SELECT id FROM link WHERE album_id IS NOT NULL");
        assertThat(call(HttpMethod.PUT, "/api/edit/links/" + linkId,
                "{\"store\":\"Bandcamp\",\"url\":\"https://band.bandcamp.com/album/renamed\",\"priceType\":\"PAID\","
                        + "\"minPrice\":7,\"currency\":\"eur\"}", artist)).isEqualTo(204);
        mvc.perform(get("/api/albums/" + bandAlbum)).andExpect(jsonPath("$.links[0].currency").value("EUR"));

        // Трэк 2 выпадае са сьпісу — і ягоная спасылка разам зь ім.
        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + bandAlbum + "/tracks",
                "{\"tracks\":[{\"number\":1,\"title\":\"One (remastered)\",\"lengthSeconds\":201}]}", artist))
                .isEqualTo(204);
        assertThat(id("SELECT count(*) FROM link")).isEqualTo(1);
        assertThat(call(HttpMethod.DELETE, "/api/edit/links/" + linkId, null, artist)).isEqualTo(204);
        assertThat(id("SELECT count(*) FROM link")).isZero();
    }

    @Test
    void cannotTouchAnotherArtistsAlbum() throws Exception {
        MockHttpSession artist = newArtistAccount();
        String album = "{\"year\":2021,\"title\":\"Mine now\",\"credit\":[{\"artist\":\"Stranger\"}]}";
        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + strangerAlbum, album, artist)).isEqualTo(403);
        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + strangerAlbum + "/tracks", "{\"tracks\":[]}", artist))
                .isEqualTo(403);
        assertThat(call(HttpMethod.DELETE, "/api/edit/albums/" + strangerAlbum, null, artist)).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/edit/albums", album, artist)).as("create for another").isEqualTo(403);
        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + bandAlbum,
                "{\"year\":2020,\"title\":\"Given away\",\"credit\":[{\"artist\":\"Stranger\"}]}", artist))
                .as("removing all own artists from the credit").isEqualTo(403);
    }

    @Test
    void createsAndDeletesOwnAlbum() throws Exception {
        MockHttpSession artist = newArtistAccount();
        var created = mvc.perform(post("/api/edit/albums").with(csrf()).session(artist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\":2026,\"title\":\"New one\",\"credit\":[{\"artist\":\"Band\"}]}"))
                .andExpect(status().isCreated()).andReturn();
        long id = Long.parseLong(created.getResponse().getContentAsString().replaceAll("\\D", ""));
        mvc.perform(get("/api/albums/" + id)).andExpect(jsonPath("$.title").value("New one"));
        assertThat(call(HttpMethod.DELETE, "/api/edit/albums/" + id, null, artist)).isEqualTo(204);
        assertThat(call(HttpMethod.GET, "/api/albums/" + id, null, null)).isEqualTo(404);
    }

    @Test
    void uploadsOwnCoverWithTheUploadItselfAsTheBasis() throws Exception {
        MockHttpSession artist = newArtistAccount();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(900, 900, BufferedImage.TYPE_INT_RGB), "png", png);
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", png.toByteArray());

        mvc.perform(multipart(HttpMethod.PUT, "/api/edit/albums/" + bandAlbum + "/cover").file(file).with(csrf())
                        .session(artist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.large").value(org.hamcrest.Matchers.startsWith("/covers/")));
        mvc.perform(multipart(HttpMethod.PUT, "/api/edit/albums/" + bandAlbum + "/cover").file(file).with(csrf())
                .session(artist)).andExpect(status().isOk());
        assertThat(id("SELECT count(*) FROM permission WHERE kind = 'WRITTEN_PERMISSION'")).as("one basis, reused")
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT proof FROM permission").query(String.class).single()).contains(ARTIST);
        mvc.perform(multipart(HttpMethod.PUT, "/api/edit/albums/" + strangerAlbum + "/cover").file(file).with(csrf())
                .session(artist)).andExpect(status().isForbidden());
    }

    @Test
    void logsEveryChangeWithWhatItReplaced() throws Exception {
        MockHttpSession artist = newArtistAccount();
        call(HttpMethod.PUT, "/api/edit/albums/" + bandAlbum,
                "{\"year\":2020,\"title\":\"Renamed\",\"credit\":[{\"artist\":\"Band\"}]}", artist);
        mvc.perform(get("/api/admin/edits").session(admin))
                .andExpect(jsonPath("$[0].email").value(ARTIST))
                .andExpect(jsonPath("$[0].action").value("ALBUM_UPDATE"))
                .andExpect(jsonPath("$[0].before.title").value("Band album"))
                .andExpect(jsonPath("$[0].after.title").value("Renamed"));
        assertThat(call(HttpMethod.GET, "/api/admin/edits", null, artist)).isEqualTo(403);
    }

    @Test
    void losesTheRightsWhenTheAdminRemovesTheAccount() throws Exception {
        MockHttpSession artist = newArtistAccount();
        long userId = id("SELECT id FROM app_user WHERE email = '" + ARTIST + "'");
        assertThat(call(HttpMethod.DELETE, "/api/super/artists/" + band + "/members/" + userId, null, admin))
                .isEqualTo(204);
        assertThat(call(HttpMethod.PUT, "/api/edit/artists/" + band, "{\"name\":\"Band\"}", artist)).isEqualTo(403);
        mvc.perform(get("/api/auth/me").session(login(ARTIST))).andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void letsNoVisitorEdit() throws Exception {
        assertThat(call(HttpMethod.PUT, "/api/edit/artists/" + band, "{\"name\":\"X\"}", null)).isEqualTo(401);
    }

    @Test
    void theSuperAdminDeletesOnlyAnEmptyArtist() throws Exception {
        assertThat(call(HttpMethod.DELETE, "/api/super/artists/" + band, null, admin)).as("has an album")
                .isEqualTo(409);
        assertThat(call(HttpMethod.DELETE, "/api/edit/albums/" + bandAlbum, null, admin)).isEqualTo(204);
        MockHttpSession artist = newArtistAccount();
        assertThat(call(HttpMethod.DELETE, "/api/super/artists/" + band, null, artist)).isEqualTo(403);
        assertThat(call(HttpMethod.DELETE, "/api/super/artists/" + band, null, admin)).as("has an account")
                .isEqualTo(409);

        long userId = id("SELECT id FROM app_user WHERE email = '" + ARTIST + "'");
        call(HttpMethod.DELETE, "/api/super/artists/" + band + "/members/" + userId, null, admin);
        assertThat(call(HttpMethod.DELETE, "/api/super/artists/" + band, null, admin)).isEqualTo(204);
        assertThat(call(HttpMethod.GET, "/api/artists/" + band, null, null)).isEqualTo(404);
        mvc.perform(get("/api/admin/edits").session(admin))
                .andExpect(jsonPath("$[0].action").value("ARTIST_DELETE"))
                .andExpect(jsonPath("$[0].subject").value("Band"));
        assertThat(call(HttpMethod.DELETE, "/api/super/artists/" + band, null, admin)).isEqualTo(404);
    }

    @Test
    void letsTheAdminEditAnyArtist() throws Exception {
        assertThat(call(HttpMethod.PUT, "/api/edit/artists/" + stranger, "{\"name\":\"Stranger\",\"aliases\":[]}",
                admin)).isEqualTo(204);
    }
}
