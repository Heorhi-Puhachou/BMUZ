package io.github.heorhipuhachou.bmuz.cover;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "bmuz.covers.local-dir=target/test-covers")
@AutoConfigureMockMvc
class CoverTest {

    private static final String ADMIN = "admin@example.org";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JsonMapper json;

    private long albumId;
    private MockHttpSession session;

    @BeforeEach
    void albumAndAdmin(@Autowired CatalogueImporter importer, @Autowired AppUserRepository users,
                       @Autowired PasswordEncoder encoder) throws Exception {
        jdbc.sql("TRUNCATE artist, album, store, app_user RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(ADMIN, encoder.encode(PASSWORD), Role.ADMIN));
        importer.merge(new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2020, "Album", List.of(new CreditEntry("Band", null)), List.of(), List.of()),
                new AlbumEntry(2021, "Other", List.of(new CreditEntry("Stranger", null)), List.of(), List.of()))));
        albumId = id("SELECT id FROM album WHERE title = 'Album'");
        session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private long id(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    /** Сапраўдны ўваход: сэсія мусіць належаць запісу з базы, інакш правы не даюцца. */
    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return r -> {
            r.setSession(session);
            return r;
        };
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder upload(byte[] file) {
        return upload(file, null, null);
    }

    private MockMultipartHttpServletRequestBuilder upload(byte[] file, String licence, String licenceUrl) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/albums/{id}/cover", albumId);
        request.file(new MockMultipartFile("file", "cover.png", "image/png", file));
        if (licence != null) {
            request.param("licence", licence);
        }
        if (licenceUrl != null) {
            request.param("licenceUrl", licenceUrl);
        }
        request.with(r -> {
            r.setMethod("PUT");
            return r;
        });
        return request;
    }

    @Test
    void storesTwoSizesAndShowsTheCover() throws Exception {
        mvc.perform(upload(png(1600, 1600)).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.large").value(org.hamcrest.Matchers.startsWith("/covers/" + albumId + "-")));

        String large = json.readTree(mvc.perform(get("/api/albums/{id}", albumId)).andReturn().getResponse()
                .getContentAsString()).get("coverUrl").asString();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(
                Files.readAllBytes(Path.of("target/test-covers").resolve(large.substring(1)))));
        assertThat(stored.getWidth()).isEqualTo(CoverImages.LARGE);

        mvc.perform(get("/api/artists"))
                .andExpect(jsonPath("$.items[0].coverUrl").value(org.hamcrest.Matchers.endsWith("-300.jpg")));
        mvc.perform(get("/api/albums/{id}", albumId)).andExpect(jsonPath("$.coverLicence").doesNotExist());
    }

    @Test
    void refusesAFileThatIsNotAnImage() throws Exception {
        mvc.perform(upload("not an image".getBytes()).with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsTheLicenceAndItsPageForAnOpenLicence() throws Exception {
        mvc.perform(upload(png(100, 100), "CC BY 4.0", null).with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(upload(png(100, 100), null, "https://band.example/album").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(upload(png(100, 100), "CC BY 4.0", "javascript:alert(1)").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void showsTheLicenceNextToAnOpenLicenceCover() throws Exception {
        mvc.perform(upload(png(400, 400), "CC BY 4.0", "https://band.example/album").with(admin()).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/albums/{id}", albumId))
                .andExpect(jsonPath("$.coverLicence").value("CC BY 4.0"))
                .andExpect(jsonPath("$.coverSource").value("https://band.example/album"));
    }

    @Test
    void refusesUploadsFromVisitors() throws Exception {
        mvc.perform(upload(png(100, 100)).with(csrf())).andExpect(status().isUnauthorized());
    }
}
