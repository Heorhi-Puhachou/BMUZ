package io.github.heorhipuhachou.bmz.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    List<Album> findByYearAndTitle(int year, String title);

    @Query("""
            SELECT a FROM Album a JOIN a.credits c
            WHERE c.artist.id = :artistId
            ORDER BY a.year, a.title
            """)
    List<Album> findByArtist(long artistId);

    /** Для кожнага выканаўцы — ягоныя альбомы ад найноўшага; першы радок па выканаўцы і ёсьць найноўшы. */
    @Query("""
            SELECT c.artist.id, a FROM Album a JOIN a.credits c
            WHERE c.artist.id IN :artistIds
            ORDER BY a.year DESC, a.id DESC
            """)
    List<Object[]> findNewestFirstByArtists(Collection<Long> artistIds);
}
