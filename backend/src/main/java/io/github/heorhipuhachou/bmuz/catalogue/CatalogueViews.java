package io.github.heorhipuhachou.bmuz.catalogue;

import java.math.BigDecimal;
import java.util.List;

public final class CatalogueViews {

    private CatalogueViews() {
    }

    public record PageView<T>(List<T> items, int page, int size, long total) {
    }

    public record ArtistSummary(long id, String name) {
    }

    public record ArtistTile(long id, String name, String coverUrl) {
    }

    public record CreditView(long artistId, String name, String joinPhrase) {
    }

    public record AlbumSummary(long id, int year, String title, String coverUrl, List<CreditView> credit) {
    }

    public record TrackHit(long id, int number, String title, List<CreditView> credit, AlbumSummary album) {
    }

    public record ArtistView(long id, String name, List<String> aliases, List<AlbumSummary> albums,
                             List<TrackHit> otherTracks) {
    }

    public record LinkView(long id, String store, String url, PriceType priceType, BigDecimal minPrice, String currency,
                           String note) {
    }

    public record TrackView(long id, int number, String title, Integer lengthSeconds, List<CreditView> credit,
                            List<LinkView> links) {
    }

    /** coverLicence і coverSource ёсьць толькі ў вокладкі пад адкрытай ліцэнзіяй: іх трэба паказваць побач. */
    public record AlbumView(long id, int year, String title, String coverUrl, String coverLicence,
                            String coverSource, List<CreditView> credit, List<TrackView> tracks,
                            List<LinkView> links) {
    }

    public record SearchResult(List<ArtistSummary> artists, List<AlbumSummary> albums) {
    }
}
