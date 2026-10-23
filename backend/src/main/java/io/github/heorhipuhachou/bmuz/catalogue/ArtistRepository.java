package io.github.heorhipuhachou.bmuz.catalogue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findFirstByName(String name);

    /** Выканаўцы, у якіх ёсьць хоць адзін бясплатны альбом. */
    @Query("SELECT ar FROM Artist ar WHERE EXISTS (SELECT a.id FROM Album a JOIN a.credits c WHERE c.artist = ar AND "
            + FreeFilter.ALBUM_A + ")")
    Page<Artist> findWithFreeAlbum(boolean freeOnly, Collection<PriceType> freeTypes, Pageable pageable);
}
