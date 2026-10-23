package io.github.heorhipuhachou.bmz.catalogue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id")
    private Album album;

    private int number;

    private String title;

    private Integer lengthSeconds;

    /** Пусты, калі ў трэка тыя самыя выканаўцы, што ў альбома. */
    @ElementCollection
    @CollectionTable(name = "track_credit", joinColumns = @JoinColumn(name = "track_id"))
    @OrderColumn(name = "position")
    private List<Credit> credits = new ArrayList<>();

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Link> links = new ArrayList<>();

    protected Track() {
    }

    Track(Album album, int number, String title, Integer lengthSeconds, List<Credit> credits) {
        this.album = album;
        this.number = number;
        this.title = title;
        this.lengthSeconds = lengthSeconds;
        this.credits.addAll(credits);
    }

    public Link addLink(Store store, LinkDetails details) {
        Link link = Link.forTrack(this, store, details);
        links.add(link);
        return link;
    }

    public Long getId() {
        return id;
    }

    public Album getAlbum() {
        return album;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public Integer getLengthSeconds() {
        return lengthSeconds;
    }

    public List<Credit> getCredits() {
        return credits;
    }

    public List<Link> getLinks() {
        return links;
    }
}
