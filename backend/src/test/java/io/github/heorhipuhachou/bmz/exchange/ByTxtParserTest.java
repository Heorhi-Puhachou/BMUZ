package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.catalogue.PriceType;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByTxtParserTest {

    private final ByTxtParser parser = new ByTxtParser();

    private CatalogueFile parseSample() throws IOException {
        Path sample = Path.of("src/test/resources/by-sample.txt");
        return parser.parse(Files.readAllLines(sample, StandardCharsets.UTF_8));
    }

    private AlbumEntry album(String title) throws IOException {
        return parseSample().albums().stream().filter(a -> a.title().equals(title)).findFirst().orElseThrow();
    }

    private static String display(List<CreditEntry> credit) {
        return credit.stream().map(c -> c.artist() + (c.join() == null ? "" : c.join())).collect(Collectors.joining());
    }

    @Test
    void skipsTheHeaderAndListsEachArtistOnce() throws IOException {
        assertThat(parseSample().artists()).extracting(ArtistEntry::name).containsExactly(
                "7 ocean", "[Für]", "Graifit", "Фантастычныя плыўцы", "Lavon Volski", "J:Mors",
                "Вольны Хор", "Вольны аркестр", "Зміцер Вайцюшкевіч");
    }

    @Test
    void readsAliasesFromBrackets() throws IOException {
        List<ArtistEntry> artists = parseSample().artists();
        assertThat(artists.get(0).aliases()).containsExactly("Seventh Ocean");
        assertThat(artists.get(1).aliases()).isEmpty();
        assertThat(artists.get(2).aliases()).containsExactly("Графит", "Графiт");
    }

    @Test
    void splitsSeveralArtistsIntoACreditThatReadsAsWritten() throws IOException {
        AlbumEntry together = album("Together");
        assertThat(together.credit()).extracting(CreditEntry::artist).containsExactly("J:Mors", "Lavon Volski");
        assertThat(display(together.credit())).isEqualTo("J:Mors & Lavon Volski");

        AlbumEntry four = album("Four");
        assertThat(four.credit()).hasSize(4);
        assertThat(display(four.credit())).isEqualTo("Вольны Хор, Вольны аркестр, Lavon Volski & Зміцер Вайцюшкевіч");
    }

    @Test
    void keepsAnAliasListInOneCredit() throws IOException {
        assertThat(album("Example").credit()).extracting(CreditEntry::artist).containsExactly("Graifit");
    }

    @Test
    void readsPriceTypes() throws IOException {
        assertThat(album("Homunculus").links().getFirst().priceType()).isEqualTo(PriceType.PAID);
        LinkEntry nyp = album("Homunculus 2").links().getFirst();
        assertThat(nyp.priceType()).isEqualTo(PriceType.NAME_YOUR_PRICE);
        assertThat(nyp.note()).isNull();
    }

    @Test
    void joinsStoreNoteAndPriceNote() throws IOException {
        LinkEntry link = album("Example").links().getFirst();
        assertThat(link.store()).isEqualTo("SoundCloud");
        assertThat(link.priceType()).isEqualTo(PriceType.FREE);
        assertThat(link.note()).isEqualTo("ultra-music.com's account; 1 of 8 tracks");
        assertThat(link.url()).isEqualTo("https://soundcloud.com/example/sets/one");
    }

    @Test
    void keepsSeveralLinksPerAlbum() throws IOException {
        assertThat(album("Дуплет").links()).hasSize(2);
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
        CatalogueFile file = parser.parse(lines);
        long links = file.albums().stream().mapToLong(a -> a.links().size()).sum();
        System.out.printf("BY.txt: %d artists, %d albums, %d links%n", file.artists().size(), file.albums().size(), links);
        file.albums().stream().filter(a -> a.credit().size() > 1)
                .forEach(a -> System.out.println("  several artists: " + display(a.credit()) + " — " + a.title()));
        assertThat(file.albums()).allSatisfy(a -> assertThat(a.links()).isNotEmpty());
    }
}
