package io.github.heorhipuhachou.bmuz.cover;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/** Вокладка альбома ў двух памерах; самі файлы ляжаць у сховішчы, тут — іх ключы і, для адкрытай ліцэнзіі, яе назва і старонка. */
@Entity
public class Cover {

    @Id
    private Long albumId;

    private String largeKey;

    private String smallKey;

    private String licence;

    private String licenceUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    private Instant uploadedAt;

    protected Cover() {
    }

    Cover(long albumId, String largeKey, String smallKey, String licence, String licenceUrl, AppUser uploadedBy) {
        this.albumId = albumId;
        this.largeKey = largeKey;
        this.smallKey = smallKey;
        this.licence = licence;
        this.licenceUrl = licenceUrl;
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

    public String getLicence() {
        return licence;
    }

    public String getLicenceUrl() {
        return licenceUrl;
    }
}
