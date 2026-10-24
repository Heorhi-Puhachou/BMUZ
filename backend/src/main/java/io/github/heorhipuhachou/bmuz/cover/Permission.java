package io.github.heorhipuhachou.bmuz.cover;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.catalogue.Artist;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

/** Падстава захоўваць і паказваць вокладкі выканаўцы. Без яе вокладку загрузіць нельга. */
@Entity
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    @Enumerated(EnumType.STRING)
    private PermissionKind kind;

    private String licence;

    private String proof;

    private String proofUrl;

    private LocalDate grantedOn;

    private String scope;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    protected Permission() {
    }

    Permission(Artist artist, PermissionKind kind, String licence, String proof, String proofUrl,
               LocalDate grantedOn, String scope, AppUser createdBy) {
        this.artist = artist;
        this.kind = kind;
        this.licence = licence;
        this.proof = proof;
        this.proofUrl = proofUrl;
        this.grantedOn = grantedOn;
        this.scope = scope;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Artist getArtist() {
        return artist;
    }

    public PermissionKind getKind() {
        return kind;
    }

    public String getLicence() {
        return licence;
    }

    public String getProof() {
        return proof;
    }

    public String getProofUrl() {
        return proofUrl;
    }

    public LocalDate getGrantedOn() {
        return grantedOn;
    }

    public String getScope() {
        return scope;
    }
}
