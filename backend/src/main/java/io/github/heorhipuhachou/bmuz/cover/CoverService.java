package io.github.heorhipuhachou.bmuz.cover;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Album;
import io.github.heorhipuhachou.bmuz.catalogue.AlbumRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Artist;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Правілы вокладак: без падставы вокладка не захоўваецца, і падстава мусіць належаць аднаму з выканаўцаў альбома —
 * дазвол аднаго гурта не адкрывае чужых альбомаў.
 */
@Service
public class CoverService {

    public record PermissionInput(long artistId, PermissionKind kind, String licence, String proof, String proofUrl,
                                  LocalDate grantedOn, String scope) {
    }

    public record PermissionView(long id, long artistId, String artistName, PermissionKind kind, String licence,
                                 String proof, String proofUrl, LocalDate grantedOn, String scope) {
    }

    /** Для адкрытай ліцэнзіі — назва ліцэнзіі і крыніца: ліцэнзіі тыпу CC BY патрабуюць паказваць іх побач з выявай. */
    public record CoverUrls(String large, String small, String licence, String source) {
    }

    static final String ARTIST_SCOPE = "вокладкі, якія выканаўца загружае сам";

    private final CoverRepository covers;
    private final PermissionRepository permissions;
    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final AppUserRepository users;
    private final CoverStorage storage;

    public CoverService(CoverRepository covers, PermissionRepository permissions, AlbumRepository albums,
                        ArtistRepository artists, AppUserRepository users, CoverStorage storage) {
        this.covers = covers;
        this.permissions = permissions;
        this.albums = albums;
        this.artists = artists;
        this.users = users;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Map<Long, CoverUrls> urls(Collection<Long> albumIds) {
        Map<Long, CoverUrls> result = new HashMap<>();
        for (Cover cover : covers.findAllById(albumIds)) {
            result.put(cover.getAlbumId(), urls(cover));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> permissionsOf(long artistId) {
        return permissions.findByArtistIdOrderByGrantedOnDesc(artistId).stream().map(CoverService::view).toList();
    }

    @Transactional
    public PermissionView addPermission(PermissionInput input, String adminEmail) {
        Artist artist = artists.findById(input.artistId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artist not found"));
        String proof = blankToNull(input.proof());
        String scope = blankToNull(input.scope());
        String licence = blankToNull(input.licence());
        String proofUrl = blankToNull(input.proofUrl());
        if (input.kind() == null || proof == null || scope == null || input.grantedOn() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind, proof, scope and date are required");
        }
        if (input.kind() == PermissionKind.OPEN_LICENCE && (licence == null || proofUrl == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "an open licence needs the licence name and the page showing it");
        }
        Permission saved = permissions.save(new Permission(artist, input.kind(), licence, proof, proofUrl,
                input.grantedOn(), scope, admin(adminEmail)));
        return view(saved);
    }

    @Transactional
    public CoverUrls upload(long albumId, long permissionId, byte[] file, String adminEmail) throws IOException {
        Album album = albums.findById(albumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "album not found"));
        Permission permission = permissions.findById(permissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no such permission"));
        boolean credited = album.getCredits().stream()
                .anyMatch(c -> c.getArtist().getId().equals(permission.getArtist().getId()));
        if (!credited) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "the permission belongs to an artist who is not credited on this album");
        }

        CoverImages.Resized resized = CoverImages.resize(file);
        String base = "covers/" + albumId + "-" + UUID.randomUUID();
        String largeKey = base + "-" + CoverImages.LARGE + ".jpg";
        String smallKey = base + "-" + CoverImages.SMALL + ".jpg";
        storage.put(largeKey, resized.large());
        storage.put(smallKey, resized.small());

        Cover old = covers.findById(albumId).orElse(null);
        if (old != null) {
            covers.delete(old);
            covers.flush();
        }
        Cover saved = covers.save(new Cover(albumId, largeKey, smallKey, permission, admin(adminEmail)));
        if (old != null) {
            storage.delete(old.getLargeKey());
            storage.delete(old.getSmallKey());
        }
        return urls(saved);
    }

    /**
     * Вокладка, якую выканаўца загружае сам са свайго ўліковага запісу. Сама загрузка і ёсьць пісьмовы дазвол: падстава
     * ствараецца адзін раз на запіс і выканаўцу і запісвае, хто яе даў.
     */
    @Transactional
    public CoverUrls uploadByArtist(long albumId, long artistId, byte[] file, String email) throws IOException {
        AppUser user = admin(email);
        Permission permission = permissions.findFirstByArtistIdAndCreatedByIdAndScope(artistId, user.getId(), ARTIST_SCOPE)
                .orElseGet(() -> permissions.save(new Permission(
                        artists.findById(artistId).orElseThrow(), PermissionKind.WRITTEN_PERMISSION, null,
                        "Загружае сам выканаўца з уліковага запісу " + user.getEmail(), null,
                        LocalDate.now(), ARTIST_SCOPE, user)));
        return upload(albumId, permission.getId(), file, email);
    }

    private static CoverUrls urls(Cover cover) {
        Permission p = cover.getPermission();
        boolean open = p.getKind() == PermissionKind.OPEN_LICENCE;
        return new CoverUrls(CoverStorage.url(cover.getLargeKey()), CoverStorage.url(cover.getSmallKey()),
                open ? p.getLicence() : null, open ? p.getProofUrl() : null);
    }

    @Transactional
    public void remove(long albumId) throws IOException {
        Cover cover = covers.findById(albumId).orElse(null);
        if (cover == null) {
            return;
        }
        covers.delete(cover);
        storage.delete(cover.getLargeKey());
        storage.delete(cover.getSmallKey());
    }

    private AppUser admin(String email) {
        return users.findByEmail(AppUser.normalise(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "unknown admin"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static PermissionView view(Permission p) {
        return new PermissionView(p.getId(), p.getArtist().getId(), p.getArtist().getName(), p.getKind(),
                p.getLicence(), p.getProof(), p.getProofUrl(), p.getGrantedOn(), p.getScope());
    }
}
