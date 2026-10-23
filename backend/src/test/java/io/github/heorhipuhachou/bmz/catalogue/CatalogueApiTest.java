package io.github.heorhipuhachou.bmz.catalogue;

import io.github.heorhipuhachou.bmz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.TrackEntry;
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
import java.util.List;

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
        jdbc.sql("TRUNCATE artist, album, store RESTART IDENTITY CASCADE").update();
        Path sample = Path.of("src/test/resources/by-sample.txt");
        assertThat(importer.importByTxt(sample)).isEqualTo(new CatalogueImporter.Result(9, 8, 0, 9));
        assertThat(importer.importByTxt(sample)).isEqualTo(new CatalogueImporter.Result(0, 0, 0, 0));

        CatalogueFile guest = new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2011, "Дуплет", List.of(new CreditEntry("Фантастычныя плыўцы", null)), List.of(),
                        List.of(new TrackEntry(1, "Песьня (feat. nastka)", 200, List.of(
                                new CreditEntry("Фантастычныя плыўцы", " feat. "),
                                new CreditEntry("nastka", null)), List.of())))));
        assertThat(importer.merge(guest)).isEqualTo(new CatalogueImporter.Result(1, 0, 1, 0));
    }

    private long id(String table, String column, String value) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE " + column + " = :v").param("v", value)
                .query(Long.class).single();
    }

    @Test
    void listsArtistsInBelarusianOrder() throws Exception {
        mvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.items[*].name").value(contains(
                        "[Für]", "7 ocean", "Вольны аркестр", "Вольны Хор", "Зміцер Вайцюшкевіч",
                        "Фантастычныя плыўцы", "Graifit", "J:Mors", "Lavon Volski", "nastka")));
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
    void showsEveryAlbumAnArtistIsCreditedOn() throws Exception {
        mvc.perform(get("/api/artists/{id}", id("artist", "name", "Lavon Volski")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albums[*].title").value(contains("Solo", "Together", "Four")))
                .andExpect(jsonPath("$.albums[1].credit[*].name").value(contains("J:Mors", "Lavon Volski")))
                .andExpect(jsonPath("$.albums[1].credit[0].joinPhrase").value(" & "));
    }

    @Test
    void showsAGuestTrackOnTheGuestsPage() throws Exception {
        mvc.perform(get("/api/artists/{id}", id("artist", "name", "nastka")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albums.length()").value(0))
                .andExpect(jsonPath("$.otherTracks[0].title").value("Песьня (feat. nastka)"))
                .andExpect(jsonPath("$.otherTracks[0].album.title").value("Дуплет"));
    }

    @Test
    void usesTheNewestAlbumForAnArtistsTile() throws Exception {
        mvc.perform(get("/api/artists"))
                .andExpect(jsonPath("$.items[8].name").value("Lavon Volski"))
                .andExpect(jsonPath("$.items[8].coverUrl").doesNotExist());
    }

    @Test
    void showsAnAlbumWithItsCreditLinksAndTracks() throws Exception {
        mvc.perform(get("/api/albums/{id}", id("album", "title", "Дуплет")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credit[0].name").value("Фантастычныя плыўцы"))
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].store").value("ultra-music.com"))
                .andExpect(jsonPath("$.links[0].priceType").value("FREE"))
                .andExpect(jsonPath("$.links[1].priceType").value("PAID"))
                .andExpect(jsonPath("$.tracks[0].credit[1].name").value("nastka"));
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
                .andExpect(jsonPath("$.albums.length()").value(2))
                .andExpect(jsonPath("$.albums[0].credit[0].name").value("7 ocean"));
    }
}
