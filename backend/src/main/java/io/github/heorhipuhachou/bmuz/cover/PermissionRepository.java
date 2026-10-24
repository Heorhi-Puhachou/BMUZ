package io.github.heorhipuhachou.bmuz.cover;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findByArtistIdOrderByGrantedOnDesc(long artistId);

    Optional<Permission> findFirstByArtistIdAndCreatedByIdAndScope(long artistId, long createdById, String scope);
}
