package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmz.catalogue.PriceType;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.TrackEntry;
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

    @Test
    void exportedJsonImportsBackWithoutChanges(@TempDir Path dir) throws Exception {
        jdbc.sql("TRUNCATE artist, store RESTART IDENTITY CASCADE").update();
        importer.importByTxt(SAMPLE);
        CatalogueFile before = exporter.export();

        Path file = dir.resolve("catalogue.json");
        exporter.exportTo(file);
        assertThat(file).content().contains("\"formatVersion\" : 1", "Фантастычныя плыўцы");

        jdbc.sql("TRUNCATE artist, store RESTART IDENTITY CASCADE").update();
        CatalogueImporter.Result result = importer.importJson(file);

        assertThat(result).isEqualTo(new CatalogueImporter.Result(4, 5, 0, 6));
        assertThat(exporter.export()).isEqualTo(before);
    }

    @Test
    void addsTracksAndTrackLinksToAnExistingAlbum() throws Exception {
        jdbc.sql("TRUNCATE artist, store RESTART IDENTITY CASCADE").update();
        importer.importByTxt(SAMPLE);

        LinkEntry freeTrack = new LinkEntry("SoundCloud", "https://soundcloud.com/example/track-1",
                PriceType.FREE, null, null, null, null);
        LinkEntry paid = new LinkEntry("Bandcamp", "https://example.bandcamp.com/album/x",
                PriceType.PAID, new BigDecimal("5.00"), "EUR", null, null);
        CatalogueFile update = new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.of(
                new ArtistEntry("Graifit", List.of(), List.of(
                        new AlbumEntry(2012, "Example", List.of(paid), List.of(
                                new TrackEntry(1, "First", 245, List.of(freeTrack)),
                                new TrackEntry(2, "Second", null, List.of())))))));

        assertThat(importer.merge(update)).isEqualTo(new CatalogueImporter.Result(0, 0, 2, 2));
        assertThat(importer.merge(update)).isEqualTo(new CatalogueImporter.Result(0, 0, 0, 0));

        AlbumEntry album = exporter.export().artists().stream()
                .filter(a -> a.name().equals("Graifit")).findFirst().orElseThrow()
                .albums().getFirst();
        assertThat(album.tracks()).extracting(TrackEntry::title).containsExactly("First", "Second");
        assertThat(album.tracks().getFirst().links()).containsExactly(freeTrack);
        assertThat(album.links()).hasSize(2).contains(paid);
    }

    @Test
    void readsAHandWrittenFileWithOmittedEmptyFields(@TempDir Path dir) throws Exception {
        jdbc.sql("TRUNCATE artist, store RESTART IDENTITY CASCADE").update();
        Path file = dir.resolve("hand.json");
        Files.writeString(file, """
                {
                  "formatVersion": 1,
                  "artists": [
                    { "name": "Hand", "albums": [ { "year": 2024, "title": "Written" } ] }
                  ]
                }
                """);
        assertThat(importer.importJson(file)).isEqualTo(new CatalogueImporter.Result(1, 1, 0, 0));
    }
}
