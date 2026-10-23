package io.github.heorhipuhachou.bmz.exchange;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.heorhipuhachou.bmz.catalogue.PriceType;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogueFile(int formatVersion, List<StoreEntry> stores, List<ArtistEntry> artists) {

    public static final int FORMAT_VERSION = 1;

    public CatalogueFile {
        stores = orEmpty(stores);
        artists = orEmpty(artists);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StoreEntry(String name, String homepage) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ArtistEntry(String name, List<String> aliases, List<AlbumEntry> albums) {

        public ArtistEntry {
            aliases = orEmpty(aliases);
            albums = orEmpty(albums);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AlbumEntry(int year, String title, List<LinkEntry> links, List<TrackEntry> tracks) {

        public AlbumEntry {
            links = orEmpty(links);
            tracks = orEmpty(tracks);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TrackEntry(int number, String title, Integer lengthSeconds, List<LinkEntry> links) {

        public TrackEntry {
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
