package io.github.heorhipuhachou.bmuz.cover;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/** Вокладка альбома ў двух памерах; самі файлы ляжаць у сховішчы, тут — іх ключы і падстава. */
@Entity
public class Cover {

    @Id
    private Long albumId;

    private String largeKey;

    private String smallKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    private Instant uploadedAt;

    protected Cover() {
    }

    Cover(long albumId, String largeKey, String smallKey, Permission permission, AppUser uploadedBy) {
        this.albumId = albumId;
        this.largeKey = largeKey;
        this.smallKey = smallKey;
        this.permission = permission;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public Long getAlbumId() {
        return albumId;
    }

    public String getLargeKey() {
        return largeKey;
    }

    public String getSmallKey() {
        return smallKey;
    }

    public Permission getPermission() {
        return permission;
    }
}
