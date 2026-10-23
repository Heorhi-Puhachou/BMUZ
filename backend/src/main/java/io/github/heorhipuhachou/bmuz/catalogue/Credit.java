package io.github.heorhipuhachou.bmuz.catalogue;

import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.util.List;
import java.util.stream.Collectors;

/** Адзін выканаўца ў крэдыце і тэкст, які ідзе пасьля ягонага імені (« & », «, » або пусты). */
@Embeddable
public class Credit {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    private String joinPhrase;

    protected Credit() {
    }

    public Credit(Artist artist, String joinPhrase) {
        this.artist = artist;
        this.joinPhrase = joinPhrase == null ? "" : joinPhrase;
    }

    public Artist getArtist() {
        return artist;
    }

    public String getJoinPhrase() {
        return joinPhrase;
    }

    /** Імёны разам са злучальным тэкстам — назва, як яе напісаў выканаўца. */
    public static String display(List<Credit> credits) {
        return credits.stream().map(c -> c.artist.getName() + c.joinPhrase).collect(Collectors.joining());
    }
}
