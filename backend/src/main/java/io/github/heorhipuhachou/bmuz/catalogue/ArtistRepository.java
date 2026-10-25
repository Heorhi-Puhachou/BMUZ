package io.github.heorhipuhachou.bmuz.catalogue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findFirstByName(String name);

    @Query("SELECT ar.id FROM Artist ar ORDER BY ar.id")
    List<Long> findAllIds();

    /** Выканаўцы, у якіх ёсьць хоць адзін альбом, што праходзіць фільтр. */
    @Query("SELECT ar FROM Artist ar WHERE EXISTS (SELECT a.id FROM Album a JOIN a.credits c WHERE c.artist = ar AND "
            + ListFilter.ALBUM_A + ")")
    Page<Artist> findWithFilteredAlbum(boolean freeOnly, Collection<PriceType> freeTypes, long uncheckedBy,
                                       Pageable pageable);
}
