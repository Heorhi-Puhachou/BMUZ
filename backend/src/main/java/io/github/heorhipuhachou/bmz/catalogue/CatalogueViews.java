package io.github.heorhipuhachou.bmz.catalogue;

import java.math.BigDecimal;
import java.util.List;

public final class CatalogueViews {

    private CatalogueViews() {
    }

    public record PageView<T>(List<T> items, int page, int size, long total) {
    }

    public record ArtistSummary(long id, String name) {
    }

    public record AlbumSummary(long id, int year, String title) {
    }

    public record AlbumHit(long id, int year, String title, ArtistSummary artist) {
    }

    public record ArtistView(long id, String name, List<String> aliases, List<AlbumSummary> albums) {
    }

    public record LinkView(String store, String url, PriceType priceType, BigDecimal minPrice, String currency,
                           String note) {
    }

    public record TrackView(long id, int number, String title, Integer lengthSeconds, List<LinkView> links) {
    }

    public record AlbumView(long id, int year, String title, ArtistSummary artist, List<TrackView> tracks,
                            List<LinkView> links) {
    }

    public record SearchResult(List<ArtistSummary> artists, List<AlbumHit> albums) {
    }
}
