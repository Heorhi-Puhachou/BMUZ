package io.github.heorhipuhachou.bmuz.exchange;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.heorhipuhachou.bmuz.catalogue.PriceType;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogueFile(int formatVersion, List<StoreEntry> stores, List<ArtistEntry> artists,
                            List<AlbumEntry> albums) {

    public static final int FORMAT_VERSION = 2;

    public CatalogueFile {
        stores = orEmpty(stores);
        artists = orEmpty(artists);
        albums = orEmpty(albums);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StoreEntry(String name, String homepage) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ArtistEntry(String name, List<String> aliases) {

        public ArtistEntry {
            aliases = orEmpty(aliases);
        }
    }

    /** Выканаўца ў крэдыце і тэкст пасьля ягонага імені, напрыклад « & ». */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CreditEntry(String artist, String join) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AlbumEntry(int year, String title, List<CreditEntry> credit, List<LinkEntry> links,
                             List<TrackEntry> tracks) {

        public AlbumEntry {
            credit = orEmpty(credit);
            links = orEmpty(links);
            tracks = orEmpty(tracks);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TrackEntry(int number, String title, Integer lengthSeconds, List<CreditEntry> credit,
                             List<LinkEntry> links) {

        public TrackEntry {
            credit = orEmpty(credit);
            links = orEmpty(links);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LinkEntry(String store, String url, PriceType priceType, BigDecimal minPrice, String currency,
                            String note, String embedCode) {
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
