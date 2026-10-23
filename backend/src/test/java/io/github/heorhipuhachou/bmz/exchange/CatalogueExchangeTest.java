package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmz.catalogue.PriceType;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.TrackEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CatalogueExchangeTest {

    private static final Path SAMPLE = Path.of("src/test/resources/by-sample.txt");

    @Autowired
    private CatalogueImporter importer;

    @Autowired
    private CatalogueExporter exporter;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyDatabase() {
        jdbc.sql("TRUNCATE artist, album, store RESTART IDENTITY CASCADE").update();
    }

    @Test
    void exportedJsonImportsBackWithoutChanges(@TempDir Path dir) throws Exception {
        importer.importByTxt(SAMPLE);
        CatalogueFile before = exporter.export();

        Path file = dir.resolve("catalogue.json");
        exporter.exportTo(file);
        assertThat(file).content().contains("\"formatVersion\" : 2", "Фантастычныя плыўцы", "\"join\" : \" & \"");

        emptyDatabase();
        assertThat(importer.importJson(file)).isEqualTo(new CatalogueImporter.Result(9, 8, 0, 9));
        assertThat(exporter.export()).isEqualTo(before);
    }

    @Test
    void addsTracksAndTrackLinksToAnExistingAlbum() throws Exception {
        importer.importByTxt(SAMPLE);

        LinkEntry freeTrack = new LinkEntry("SoundCloud", "https://soundcloud.com/example/track-1",
                PriceType.FREE, null, null, null, null);
        LinkEntry paid = new LinkEntry("Bandcamp", "https://example.bandcamp.com/album/x",
                PriceType.PAID, new BigDecimal("5.00"), "EUR", null, null);
        List<CreditEntry> graifit = List.of(new CreditEntry("Graifit", null));
        CatalogueFile update = new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2012, "Example", graifit, List.of(paid), List.of(
                        new TrackEntry(1, "First", 245, List.of(), List.of(freeTrack)),
                        new TrackEntry(2, "Second", null, List.of(), List.of())))));

        assertThat(importer.merge(update)).isEqualTo(new CatalogueImporter.Result(0, 0, 2, 2));
        assertThat(importer.merge(update)).isEqualTo(new CatalogueImporter.Result(0, 0, 0, 0));

        AlbumEntry album = exporter.export().albums().stream()
                .filter(a -> a.title().equals("Example")).findFirst().orElseThrow();
        assertThat(album.tracks()).extracting(TrackEntry::title).containsExactly("First", "Second");
        assertThat(album.tracks().getFirst().links()).containsExactly(freeTrack);
        assertThat(album.links()).hasSize(2).contains(paid);
    }

    @Test
    void keepsAlbumsWithTheSameTitleByDifferentArtistsApart() {
        CatalogueFile two = new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(), List.of(
                new AlbumEntry(2020, "Same", List.of(new CreditEntry("One", null)), List.of(), List.of()),
                new AlbumEntry(2020, "Same", List.of(new CreditEntry("Two", null)), List.of(), List.of())));
        assertThat(importer.merge(two)).isEqualTo(new CatalogueImporter.Result(2, 2, 0, 0));
    }

    @Test
    void readsAHandWrittenFileWithOmittedEmptyFields(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hand.json");
        Files.writeString(file, """
                {
                  "formatVersion": 2,
                  "albums": [
                    { "year": 2024, "title": "Written", "credit": [ { "artist": "Hand" } ] }
                  ]
                }
                """);
        assertThat(importer.importJson(file)).isEqualTo(new CatalogueImporter.Result(1, 1, 0, 0));
    }
}
