package io.github.heorhipuhachou.bmz.catalogue;

import io.github.heorhipuhachou.bmz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmz.exchange.CatalogueImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeAll
    void importSample(@Autowired CatalogueImporter importer) throws Exception {
        jdbc.sql("TRUNCATE artist, store RESTART IDENTITY CASCADE").update();
        Path sample = Path.of("src/test/resources/by-sample.txt");
        assertThat(importer.importByTxt(sample)).isEqualTo(new CatalogueImporter.Result(4, 5, 0, 6));
        assertThat(importer.importByTxt(sample)).isEqualTo(new CatalogueImporter.Result(0, 0, 0, 0));
    }

    private long id(String table, String column, String value) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE " + column + " = :v").param("v", value)
                .query(Long.class).single();
    }

    @Test
    void listsArtistsInBelarusianOrder() throws Exception {
        mvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[*].name")
                        .value(contains("[Für]", "7 ocean", "Фантастычныя плыўцы", "Graifit")));
    }

    @Test
    void showsAnArtistWithAliasesAndAlbums() throws Exception {
        mvc.perform(get("/api/artists/{id}", id("artist", "name", "7 ocean")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aliases[0]").value("Seventh Ocean"))
                .andExpect(jsonPath("$.albums.length()").value(2))
                .andExpect(jsonPath("$.albums[1].title").value("Homunculus 2"));
    }

    @Test
    void showsAnAlbumWithItsLinks() throws Exception {
        mvc.perform(get("/api/albums/{id}", id("album", "title", "Дуплет")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artist.name").value("Фантастычныя плыўцы"))
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].store").value("ultra-music.com"))
                .andExpect(jsonPath("$.links[0].priceType").value("FREE"))
                .andExpect(jsonPath("$.links[1].priceType").value("PAID"));
    }

    @Test
    void answersNotFoundForAMissingAlbum() throws Exception {
        mvc.perform(get("/api/albums/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void findsAnArtistByAlias() throws Exception {
        mvc.perform(get("/api/search").param("q", "Графит"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artists[0].name").value("Graifit"));
    }

    @Test
    void findsAlbumsDespiteATypo() throws Exception {
        mvc.perform(get("/api/search").param("q", "Homunculs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albums.length()").value(2));
    }
}
