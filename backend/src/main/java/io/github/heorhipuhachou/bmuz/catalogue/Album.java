package io.github.heorhipuhachou.bmuz.catalogue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int year;

    private String title;

    /** Ставіць база і праўка каталёга радком SQL; тут — толькі для чытаньня. */
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "album_credit", joinColumns = @JoinColumn(name = "album_id"))
    @OrderColumn(name = "position")
    private List<Credit> credits = new ArrayList<>();

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number")
    private List<Track> tracks = new ArrayList<>();

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Link> links = new ArrayList<>();

    protected Album() {
    }

    public Album(int year, String title, List<Credit> credits) {
        if (credits.isEmpty()) {
            throw new IllegalArgumentException("an album needs at least one artist: " + title);
        }
        this.year = year;
        this.title = title;
        this.credits.addAll(credits);
    }

    public Track addTrack(int number, String title, Integer lengthSeconds, List<Credit> credits) {
        Track track = new Track(this, number, title, lengthSeconds, credits);
        tracks.add(track);
        return track;
    }

    public Link addLink(Store store, LinkDetails details) {
        Link link = Link.forAlbum(this, store, details);
        links.add(link);
        return link;
    }

    public Long getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public String getTitle() {
        return title;
    }

    public List<Credit> getCredits() {
        return credits;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public List<Link> getLinks() {
        return links;
    }
}
