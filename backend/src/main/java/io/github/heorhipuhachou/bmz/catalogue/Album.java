package io.github.heorhipuhachou.bmz.catalogue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    private int year;

    private String title;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number")
    private List<Track> tracks = new ArrayList<>();

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Link> links = new ArrayList<>();

    protected Album() {
    }

    Album(Artist artist, int year, String title) {
        this.artist = artist;
        this.year = year;
        this.title = title;
    }

    public Track addTrack(int number, String title, Integer lengthSeconds) {
        Track track = new Track(this, number, title, lengthSeconds);
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

    public Artist getArtist() {
        return artist;
    }

    public int getYear() {
        return year;
    }

    public String getTitle() {
        return title;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public List<Link> getLinks() {
        return links;
    }
}
