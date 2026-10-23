package io.github.heorhipuhachou.bmz.catalogue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<ArtistAlias> aliases = new ArrayList<>();

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("year, title")
    private List<Album> albums = new ArrayList<>();

    protected Artist() {
    }

    public Artist(String name) {
        this.name = name;
    }

    public void addAlias(String aliasName) {
        aliases.add(new ArtistAlias(this, aliasName));
    }

    public Album addAlbum(int year, String title) {
        Album album = new Album(this, year, title);
        albums.add(album);
        return album;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<ArtistAlias> getAliases() {
        return aliases;
    }

    public List<Album> getAlbums() {
        return albums;
    }
}
