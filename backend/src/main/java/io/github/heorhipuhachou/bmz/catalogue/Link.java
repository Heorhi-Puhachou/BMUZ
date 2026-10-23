package io.github.heorhipuhachou.bmz.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;

@Entity
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    private String url;

    @Enumerated(EnumType.STRING)
    private PriceType priceType;

    private BigDecimal minPrice;

    private String currency;

    private String note;

    private String embedCode;

    protected Link() {
    }

    private Link(Store store, Album album, Track track, LinkDetails details) {
        this.store = store;
        this.album = album;
        this.track = track;
        this.url = details.url();
        this.priceType = details.priceType();
        this.minPrice = details.minPrice();
        this.currency = details.currency();
        this.note = details.note();
        this.embedCode = details.embedCode();
    }

    static Link forAlbum(Album album, Store store, LinkDetails details) {
        return new Link(store, album, null, details);
    }

    static Link forTrack(Track track, Store store, LinkDetails details) {
        return new Link(store, null, track, details);
    }

    public LinkDetails details() {
        return new LinkDetails(url, priceType, minPrice, currency, note, embedCode);
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public String getUrl() {
        return url;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNote() {
        return note;
    }

    public String getEmbedCode() {
        return embedCode;
    }
}
