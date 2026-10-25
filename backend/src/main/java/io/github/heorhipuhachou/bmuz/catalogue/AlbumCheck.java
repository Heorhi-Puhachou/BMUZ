package io.github.heorhipuhachou.bmuz.catalogue;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Прагляд альбома адміністратарам — толькі каб {@link ListFilter} мог спытаць пра яго ў JPQL. Пішацца ён праз
 * {@code AlbumChecks} радкамі SQL.
 */
@Entity
@Immutable
public class AlbumCheck {

    @Embeddable
    public record Key(Long albumId, Long userId) {
    }

    @EmbeddedId
    private Key id;

    private Instant checkedAt;

    protected AlbumCheck() {
    }
}
