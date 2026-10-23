package io.github.heorhipuhachou.bmz.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findFirstByName(String name);
}
