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
@SpringBootTest(properties = "bmuz.site-url=https://example.test/")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SitemapControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private long albumId;
    private long artistId;

    @BeforeAll
    void addAlbum(@Autowired CatalogueImporter importer) {
        jdbc.sql("TRUNCATE artist, album, store RESTART IDENTITY CASCADE").update();
        importer.merge(new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2020, "Дыхаем", List.of(new CreditEntry("Lavon Volski", null)),
                        List.of(), List.of()))));
        albumId = jdbc.sql("SELECT id FROM album").query(Long.class).single();
        artistId = jdbc.sql("SELECT id FROM artist").query(Long.class).single();
    }

    @Test
    void listsTheHomePageAndEveryArtistAndAlbum() throws Exception {
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<loc>https://example.test/</loc>")))
                .andExpect(content().string(containsString("<loc>https://example.test/about</loc>")))
                .andExpect(content().string(containsString("<loc>https://example.test/artists/" + artistId + "</loc>")))
                .andExpect(content().string(containsString("<loc>https://example.test/albums/" + albumId + "</loc>")))
                .andExpect(content().string(not(containsString("/login"))));
    }

    @Test
    void robotsKeepsOutOfTheAccountPagesAndNamesTheSitemap() throws Exception {
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("Disallow: /login\n")))
                .andExpect(content().string(not(containsString("/api"))))
                .andExpect(content().string(containsString("Sitemap: https://example.test/sitemap.xml\n")));
    }
}
