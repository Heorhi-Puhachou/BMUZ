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

    /** Сярэдняя ацэнка ад 1 да 10 і колькі водгукаў яе далі. */
    public record Rating(double average, int count) {
    }

    /** rating — сярэдняя ўсіх водгукаў на альбомы выканаўцы; null, калі іх няма або водгукі выключаныя. */
    public record ArtistTile(long id, String name, String coverUrl, Rating rating) {
    }

    public record CreditView(long artistId, String name, String joinPhrase) {
    }

    /** rating — null, калі водгукаў няма або яны выключаныя. */
    public record AlbumSummary(long id, int year, String title, String coverUrl, List<CreditView> credit,
                               Rating rating) {
    }

    public record TrackHit(long id, int number, String title, List<CreditView> credit, AlbumSummary album) {
    }

    /**
     * contacts — тое, што выканаўца сам паказвае на сваёй старонцы; пусты сьпіс, калі нічога. imageUrl — выява, якую
     * выканаўца загрузіў сам; null, калі няма.
     */
    public record ArtistView(long id, String name, List<String> aliases, List<AlbumSummary> albums,
                             List<TrackHit> otherTracks, List<ContactView> contacts, String imageUrl) {
    }

    public record ContactView(long id, ContactKind kind, String value) {
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
