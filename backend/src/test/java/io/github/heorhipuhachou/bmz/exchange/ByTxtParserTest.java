package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.catalogue.PriceType;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByTxtParserTest {

    private final ByTxtParser parser = new ByTxtParser();

    private List<ArtistEntry> parseSample() throws IOException {
        Path sample = Path.of("src/test/resources/by-sample.txt");
        return parser.parse(Files.readAllLines(sample, StandardCharsets.UTF_8)).artists();
    }

    @Test
    void skipsTheHeaderAndReadsEveryArtist() throws IOException {
        assertThat(parseSample()).extracting(ArtistEntry::name)
                .containsExactly("7 ocean", "[Für]", "Graifit", "Фантастычныя плыўцы");
    }

    @Test
    void readsAliasesFromBrackets() throws IOException {
        List<ArtistEntry> artists = parseSample();
        assertThat(artists.get(0).aliases()).containsExactly("Seventh Ocean");
        assertThat(artists.get(1).aliases()).isEmpty();
        assertThat(artists.get(2).aliases()).containsExactly("Графит", "Графiт");
    }

    @Test
    void readsPriceTypes() throws IOException {
        List<LinkEntry> links = parseSample().get(0).albums().stream()
                .flatMap(a -> a.links().stream()).toList();
        assertThat(links).extracting(LinkEntry::priceType)
                .containsExactly(PriceType.PAID, PriceType.NAME_YOUR_PRICE);
        assertThat(links.get(1).note()).isNull();
    }

    @Test
    void joinsStoreNoteAndPriceNote() throws IOException {
        LinkEntry link = parseSample().get(2).albums().get(0).links().get(0);
        assertThat(link.store()).isEqualTo("SoundCloud");
        assertThat(link.priceType()).isEqualTo(PriceType.FREE);
        assertThat(link.note()).isEqualTo("ultra-music.com's account; 1 of 8 tracks");
        assertThat(link.url()).isEqualTo("https://soundcloud.com/example/sets/one");
    }

    @Test
    void keepsSeveralLinksPerAlbum() throws IOException {
        assertThat(parseSample().get(3).albums().get(0).links()).hasSize(2);
    }

    @Test
    void reportsTheLineOfAnUnreadableEntry() {
        List<String> lines = List.of("Artist", "  2020  Album", "        no colon here  https://example.com");
        assertThatThrownBy(() -> parser.parse(lines))
                .isInstanceOf(ByTxtFormatException.class)
                .hasMessageStartingWith("BY.txt line 3");
    }

    @Test
    @EnabledIfSystemProperty(named = "bmz.byTxt", matches = ".+")
    void readsTheRealFile() throws IOException {
        List<String> lines = Files.readAllLines(Path.of(System.getProperty("bmz.byTxt")), StandardCharsets.UTF_8);
        List<ArtistEntry> artists = parser.parse(lines).artists();
        long albums = artists.stream().mapToLong(a -> a.albums().size()).sum();
        long links = artists.stream().flatMap(a -> a.albums().stream()).mapToLong(a -> a.links().size()).sum();
        System.out.printf("BY.txt: %d artists, %d albums, %d links%n", artists.size(), albums, links);
        assertThat(artists).allSatisfy(a -> assertThat(a.albums()).allSatisfy(al -> assertThat(al.links()).isNotEmpty()));
    }
}
