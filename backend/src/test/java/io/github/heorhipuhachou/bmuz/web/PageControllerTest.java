package io.github.heorhipuhachou.bmuz.web;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PageControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private long albumId;

    @BeforeAll
    void addAlbum(@Autowired CatalogueImporter importer) {
        jdbc.sql("TRUNCATE artist, album, store RESTART IDENTITY CASCADE").update();
        importer.merge(new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2020, "Дыхаем <зноў>", List.of(
                        new CreditEntry("J:Mors", " & "), new CreditEntry("Lavon Volski", null)),
                        List.of(), List.of()))));
        albumId = jdbc.sql("SELECT id FROM album").query(Long.class).single();
    }

    @Test
    void writesTheAlbumsTitleAndDescriptionIntoThePage() throws Exception {
        mvc.perform(get("/albums/{id}", albumId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(
                        "<title>Дыхаем &lt;зноў&gt; — J:Mors &amp; Lavon Volski — Беларуская МУЗыка</title>")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:description\" content=\"J:Mors &amp; Lavon Volski, 2020.")))
                .andExpect(content().string(containsString("<div id=\"root\"></div>")));
    }

    @Test
    void answersNotFoundWithTheSitePageForAMissingAlbum() throws Exception {
        mvc.perform(get("/albums/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("<title>Беларуская МУЗыка</title>")))
                .andExpect(content().string(not(containsString("Дыхаем"))));
    }

    @Test
    void servesTheHomePage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("og:site_name")));
    }

    @Test
    void servesTheAccountPagesOpenedFromAMail() throws Exception {
        for (String path : new String[]{"/login", "/admin", "/register", "/confirm?token=x", "/forgot", "/reset?token=x"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<div id=\"root\"></div>")));
        }
    }

    @Test
    void leavesTheApiAlone() throws Exception {
        mvc.perform(get("/api/albums/{id}", albumId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
