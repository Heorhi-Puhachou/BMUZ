package io.github.heorhipuhachou.bmuz.catalogue;

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

    protected Artist() {
    }

    public Artist(String name) {
        this.name = name;
    }

    public void addAlias(String aliasName) {
        aliases.add(new ArtistAlias(this, aliasName));
    }

    public void rename(String name) {
        this.name = name;
    }

    /** Застаюцца толькі названыя псэўданімы, у тым ліку ўжо вядомыя; новыя дадаюцца. */
    public void replaceAliases(List<String> names) {
        aliases.removeIf(a -> !names.contains(a.getName()));
        List<String> known = aliases.stream().map(ArtistAlias::getName).toList();
        names.stream().filter(n -> !known.contains(n)).forEach(this::addAlias);
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
}
