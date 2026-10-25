package io.github.heorhipuhachou.bmuz.editing;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.account.Role;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueImporter;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "bmuz.covers.local-dir=target/test-covers")
@AutoConfigureMockMvc
class AlbumCheckTest {

    private static final String SUPER = "super@example.org";
    private static final String ADMIN = "admin@example.org";
    private static final String USER = "user@example.org";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private MockHttpSession superAdmin;
    private MockHttpSession admin;
    private long first;
    private long second;

    @BeforeEach
    void catalogueAndAdmins(@Autowired CatalogueImporter importer, @Autowired AppUserRepository users,
                            @Autowired PasswordEncoder encoder) throws Exception {
        jdbc.sql("TRUNCATE artist, album, store, app_user, edit_log RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(SUPER, encoder.encode(PASSWORD), Role.SUPER_ADMIN));
        users.save(new AppUser(ADMIN, encoder.encode(PASSWORD), Role.ADMIN));
        users.save(new AppUser(USER, encoder.encode(PASSWORD), Role.ADMIN));
        jdbc.sql("UPDATE app_user SET role = 'USER' WHERE email = ?").param(USER).update();
        importer.merge(new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2020, "First", List.of(new CreditEntry("Band", null)), List.of(), List.of()),
                new AlbumEntry(2021, "Second", List.of(new CreditEntry("Band", null)), List.of(), List.of()))));
        first = id("SELECT id FROM album WHERE title = 'First'");
        second = id("SELECT id FROM album WHERE title = 'Second'");
        superAdmin = login(SUPER);
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

    @Test
    void eachAdminChecksForThemselves() throws Exception {
        mvc.perform(get("/api/admin/albums").session(admin))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].checkedAt").value(nullValue()));

        assertThat(call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin)).isEqualTo(204);
        assertThat(call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin)).as("again")
                .isEqualTo(204);
        mvc.perform(get("/api/admin/albums").param("checked", "false").session(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second));
        mvc.perform(get("/api/admin/albums").param("checked", "true").session(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[0].credit[0].name").value("Band"));
        mvc.perform(get("/api/admin/albums").session(superAdmin)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/admin/albums/" + first + "/checks").session(superAdmin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(ADMIN))
                .andExpect(jsonPath("$[0].changedSince").value(false));

        assertThat(call(HttpMethod.DELETE, "/api/admin/albums/" + first + "/check", null, admin)).isEqualTo(204);
        mvc.perform(get("/api/admin/albums").session(admin)).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void anAlbumChangedAfterTheCheckComesBackFirst() throws Exception {
        call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin);
        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + first + "/tracks",
                "{\"tracks\":[{\"number\":1,\"title\":\"One\"}]}", superAdmin)).isEqualTo(204);

        mvc.perform(get("/api/admin/albums").session(admin))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[0].checkedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].checkedAt").value(nullValue()));
        mvc.perform(get("/api/admin/albums/" + first + "/checks").session(admin))
                .andExpect(jsonPath("$[0].changedSince").value(true));

        call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin);
        mvc.perform(get("/api/admin/albums").session(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second));
    }

    @Test
    void listsTheIdsOfTheUncheckedAlbums() throws Exception {
        mvc.perform(get("/api/admin/albums/unchecked").session(admin))
                .andExpect(jsonPath("$.albumIds.length()").value(2));
        call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin);
        mvc.perform(get("/api/admin/albums/unchecked").session(admin))
                .andExpect(jsonPath("$.albumIds.length()").value(1))
                .andExpect(jsonPath("$.albumIds[0]").value(second));
        assertThat(call(HttpMethod.GET, "/api/admin/albums/unchecked", null, login(USER))).isEqualTo(403);
    }

    @Test
    void theCatalogueListsFilterToTheAdminsUncheckedAlbums() throws Exception {
        long band = id("SELECT id FROM artist WHERE name = 'Band'");
        call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin);

        mvc.perform(get("/api/artists/" + band).param("unchecked", "true").session(admin))
                .andExpect(jsonPath("$.albums.length()").value(1))
                .andExpect(jsonPath("$.albums[0].title").value("Second"));
        // Прагляд аднаго адміністратара не здымае альбома ў сьпісе іншага.
        mvc.perform(get("/api/artists/" + band).param("unchecked", "true").session(superAdmin))
                .andExpect(jsonPath("$.albums.length()").value(2));
        mvc.perform(get("/api/search").param("q", "First").param("unchecked", "true").session(admin))
                .andExpect(jsonPath("$.albums").isEmpty());

        call(HttpMethod.PUT, "/api/admin/albums/" + second + "/check", null, admin);
        mvc.perform(get("/api/artists").param("unchecked", "true").session(admin))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/search").param("q", "Band").param("unchecked", "true").session(admin))
                .andExpect(jsonPath("$.artists").isEmpty());
        mvc.perform(get("/api/artists").session(admin)).andExpect(jsonPath("$.total").value(1));

        assertThat(call(HttpMethod.PUT, "/api/edit/albums/" + first + "/tracks",
                "{\"tracks\":[{\"number\":1,\"title\":\"One\"}]}", superAdmin)).isEqualTo(204);
        mvc.perform(get("/api/artists").param("unchecked", "true").session(admin))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void onlyAnAdminFiltersByChecks() throws Exception {
        assertThat(call(HttpMethod.GET, "/api/artists?unchecked=true", null, null)).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/api/artists?unchecked=true", null, login(USER))).isEqualTo(403);
    }

    @Test
    void theChecksGoWithTheAlbum() throws Exception {
        call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin);
        assertThat(call(HttpMethod.DELETE, "/api/edit/albums/" + first, null, superAdmin)).isEqualTo(204);
        assertThat(id("SELECT count(*) FROM album_check")).isZero();
        assertThat(call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, admin)).isEqualTo(404);
        assertThat(call(HttpMethod.GET, "/api/admin/albums/" + first + "/checks", null, admin)).isEqualTo(404);
    }

    @Test
    void onlyAdminsCheck() throws Exception {
        assertThat(call(HttpMethod.GET, "/api/admin/albums", null, null)).isEqualTo(401);
        MockHttpSession user = login(USER);
        assertThat(call(HttpMethod.GET, "/api/admin/albums", null, user)).isEqualTo(403);
        assertThat(call(HttpMethod.PUT, "/api/admin/albums/" + first + "/check", null, user)).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/api/admin/albums?size=500", null, admin)).isEqualTo(400);
    }
}
