package io.github.heorhipuhachou.bmuz.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {

    /** Трэкі, дзе выканаўца названы асобна, — напрыклад, госьць на чужым альбоме. */
    @Query("SELECT t FROM Track t JOIN t.credits c JOIN FETCH t.album a WHERE c.artist.id = :artistId AND "
            + ListFilter.ALBUM_A + " ORDER BY a.year, a.title, t.number")
    List<Track> findCreditedToArtist(long artistId, boolean freeOnly, Collection<PriceType> freeTypes, long uncheckedBy);
}
