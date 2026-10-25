package io.github.heorhipuhachou.bmuz.editing;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.account.TeamService;
import io.github.heorhipuhachou.bmuz.catalogue.Album;
import io.github.heorhipuhachou.bmuz.catalogue.AlbumRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Artist;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistAlias;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistRepository;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueService;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.AlbumView;
import io.github.heorhipuhachou.bmuz.catalogue.ContactKind;
import io.github.heorhipuhachou.bmuz.catalogue.Credit;
import io.github.heorhipuhachou.bmuz.catalogue.Link;
import io.github.heorhipuhachou.bmuz.catalogue.LinkDetails;
import io.github.heorhipuhachou.bmuz.catalogue.LinkRepository;
import io.github.heorhipuhachou.bmuz.catalogue.PriceType;
import io.github.heorhipuhachou.bmuz.catalogue.Store;
import io.github.heorhipuhachou.bmuz.catalogue.StoreRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Track;
import io.github.heorhipuhachou.bmuz.cover.CoverService;
import io.github.heorhipuhachou.bmuz.cover.CoverService.CoverUrls;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Праўка каталёга — для адміністратара і для выканаўцаў. Выканаўца праўляе свайго выканаўцу і альбомы, у крэдыце якіх
 * ён ёсьць. Зьмены бачныя на сайце адразу, і кожная запісваецца ў {@link EditLog} разам з тым, што было да яе.
 */
@Service
public class CatalogueEditor {

    public record ArtistInput(String name, List<String> aliases) {
    }

    public record CreditInput(String artist, String join) {
    }

    public record AlbumInput(Integer year, String title, List<CreditInput> credit) {
    }

    public record TrackInput(int number, String title, Integer lengthSeconds) {
    }

    public record TracksInput(List<TrackInput> tracks) {
    }

    /** trackNumber — спасылка на адзін трэк; пусты — на ўвесь альбом. */
    public record LinkInput(String store, String url, PriceType priceType, BigDecimal minPrice, String currency,
                            String note, Integer trackNumber) {
    }

    public record Created(long id) {
    }

    public record ContactInput(ContactKind kind, String value) {
    }

    public record ContactsInput(List<ContactInput> contacts) {
    }

    private record ArtistState(String name, List<String> aliases) {
    }

    private record ContactState(ContactKind kind, String value) {
    }

    static final int MAX_CONTACTS = 20;
    static final int MAX_CONTACT_LENGTH = 300;

    private final AppUserRepository users;
    private final TeamService team;
    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final LinkRepository links;
    private final StoreRepository stores;
    private final CatalogueService catalogue;
    private final CoverService covers;
    private final EditLog log;
    private final JdbcClient jdbc;
    private final EntityManager entities;

    CatalogueEditor(AppUserRepository users, TeamService team, ArtistRepository artists,
                    AlbumRepository albums, LinkRepository links, StoreRepository stores, CatalogueService catalogue,
                    CoverService covers, EditLog log, JdbcClient jdbc, EntityManager entities) {
        this.users = users;
        this.team = team;
        this.artists = artists;
        this.albums = albums;
        this.links = links;
        this.stores = stores;
        this.catalogue = catalogue;
        this.covers = covers;
        this.log = log;
        this.jdbc = jdbc;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public List<String> storeNames() {
        return stores.findAll().stream().map(Store::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Transactional
    public void updateArtist(long artistId, ArtistInput input, String email) {
        Editor editor = editor(email);
        Artist artist = artists.findById(artistId).orElseThrow(() -> notFound("artist"));
        require(editor.manages(artistId));
        String name = required(input.name(), "name");
        boolean taken = artists.findFirstByName(name).filter(other -> !other.getId().equals(artistId)).isPresent();
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "another artist already has this name");
        }
        List<String> aliases = new ArrayList<>(new LinkedHashSet<>((input.aliases() == null ? List.<String>of()
                : input.aliases()).stream().map(CatalogueEditor::blankToNull).filter(Objects::nonNull)
                .filter(a -> !a.equals(name)).toList()));

        ArtistState before = state(artist);
        artist.rename(name);
        artist.replaceAliases(aliases);
        log.record(editor, "ARTIST_UPDATE", name, artistId, null, before, new ArtistState(name, aliases));
    }

    /**
     * Кантакты піша толькі ўліковы запіс самога выканаўцы — адміністратар таксама не: паказваць іх ці не, вырашае
     * выканаўца. Сьпіс замяняецца цалкам; аднолькавыя радкі застаюцца адным.
     */
    @Transactional
    public void replaceContacts(long artistId, ContactsInput input, String email) {
        Editor editor = editor(email);
        Artist artist = artists.findById(artistId).orElseThrow(() -> notFound("artist"));
        if (!editor.artists().contains(artistId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the artist's own account writes contacts");
        }
        List<ContactState> after = new ArrayList<>(new LinkedHashSet<>((input.contacts() == null
                ? List.<ContactInput>of() : input.contacts()).stream().map(CatalogueEditor::contact).toList()));
        if (after.size() > MAX_CONTACTS) {
            throw bad("at most " + MAX_CONTACTS + " contacts");
        }
        List<ContactState> before = contacts(artistId);
        jdbc.sql("DELETE FROM artist_contact WHERE artist_id = ?").param(artistId).update();
        for (int i = 0; i < after.size(); i++) {
            jdbc.sql("INSERT INTO artist_contact (artist_id, position, kind, value) VALUES (?, ?, ?, ?)")
                    .params(artistId, i, after.get(i).kind().name(), after.get(i).value()).update();
        }
        log.record(editor, "CONTACTS_UPDATE", artist.getName(), artistId, null, before, after);
    }

    /** Адміністратар выдаляе кантакт — напрыклад, спасылку на незаконную копію, — але новых не піша. */
    @Transactional
    public void removeContact(long contactId, String email) {
        Editor editor = editor(email);
        require(editor.admin());
        record Row(long artistId, String artist, ContactState contact) {
        }
        Row row = jdbc.sql("""
                        SELECT c.artist_id, a.name, c.kind, c.value
                        FROM artist_contact c JOIN artist a ON a.id = c.artist_id WHERE c.id = ?""")
                .param(contactId)
                .query((rs, n) -> new Row(rs.getLong(1), rs.getString(2),
                        new ContactState(ContactKind.valueOf(rs.getString(3)), rs.getString(4))))
                .optional().orElseThrow(() -> notFound("contact"));
        jdbc.sql("DELETE FROM artist_contact WHERE id = ?").param(contactId).update();
        log.record(editor, "CONTACT_REMOVE", row.artist(), row.artistId(), null, row.contact(), null);
    }

    /**
     * Толькі пусты выканаўца: без альбомаў, трэкаў, падставы для вокладак і ўліковых запісаў. Альбомы выдаляюцца
     * паасобку, каб кожны застаўся ў журнале; падстава — доказ дазволу, і яе не выдаляюць мімаходзь.
     */
    @Transactional
    public void deleteArtist(long artistId, String email) {
        Editor editor = editor(email);
        Artist artist = artists.findById(artistId).orElseThrow(() -> notFound("artist"));
        if (uses("album_credit", artistId) || uses("track_credit", artistId)) {
            throw conflict("the artist still has albums or tracks");
        }
        if (uses("permission", artistId)) {
            throw conflict("the artist has a cover basis on record");
        }
        if (uses("artist_member", artistId)) {
            throw conflict("the artist still has accounts; remove them on the team panel first");
        }
        log.record(editor, "ARTIST_DELETE", artist.getName(), artistId, null, state(artist), null);
        artists.delete(artist);
    }

    @Transactional
    public Created createAlbum(AlbumInput input, String email) {
        Editor editor = editor(email);
        int year = year(input.year());
        String title = required(input.title(), "title");
        List<Credit> credit = credit(input.credit());
        require(editor.managesAny(credit.stream().map(c -> c.getArtist().getId()).toList()));

        Album album = albums.save(new Album(year, title, credit));
        AlbumView after = fresh(album.getId());
        log.record(editor, "ALBUM_CREATE", title, credit.getFirst().getArtist().getId(), album.getId(), null, after);
        return new Created(album.getId());
    }

    /** Год, назва і крэдыт. Выканаўца ня можа прыбраць з крэдыту ўсіх сваіх выканаўцаў: альбом выйшаў бы зь ягоных рук. */
    @Transactional
    public void updateAlbum(long albumId, AlbumInput input, String email) {
        Editor editor = editor(email);
        Album album = managedAlbum(albumId, editor);
        int year = year(input.year());
        String title = required(input.title(), "title");
        List<Credit> credit = credit(input.credit());
        require(editor.managesAny(credit.stream().map(c -> c.getArtist().getId()).toList()));

        AlbumView before = catalogue.album(album.getId());
        entities.flush();
        // Крэдыт перапісваецца радкамі: перастаноўка выканаўцаў праз JPA парушыла б унікальнасьць (альбом, выканаўца).
        jdbc.sql("UPDATE album SET year = ?, title = ?, updated_at = now() WHERE id = ?")
                .params(year, title, albumId).update();
        jdbc.sql("DELETE FROM album_credit WHERE album_id = ?").param(albumId).update();
        for (int i = 0; i < credit.size(); i++) {
            jdbc.sql("INSERT INTO album_credit (album_id, position, artist_id, join_phrase) VALUES (?, ?, ?, ?)")
                    .params(albumId, i, credit.get(i).getArtist().getId(), credit.get(i).getJoinPhrase()).update();
        }
        AlbumView after = fresh(albumId);
        log.record(editor, "ALBUM_UPDATE", title, after.credit().getFirst().artistId(), albumId, before, after);
    }

    @Transactional
    public void deleteAlbum(long albumId, String email) throws IOException {
        Editor editor = editor(email);
        Album album = managedAlbum(albumId, editor);
        AlbumView before = catalogue.album(albumId);
        log.record(editor, "ALBUM_DELETE", album.getTitle(), before.credit().getFirst().artistId(), albumId, before,
                null);
        covers.remove(albumId);
        albums.delete(album);
    }

    /**
     * Увесь сьпіс трэкаў адразу. Трэкі супастаўляюцца па нумары: існы трэк з тым самым нумарам атрымлівае новую назву й
     * даўжыню і захоўвае свае спасылкі, трэк, якога ў сьпісе няма, выдаляецца разам са спасылкамі.
     */
    @Transactional
    public void replaceTracks(long albumId, TracksInput input, String email) {
        Editor editor = editor(email);
        Album album = managedAlbum(albumId, editor);
        List<TrackInput> wanted = input.tracks() == null ? List.of() : input.tracks();
        Set<Integer> numbers = new HashSet<>();
        for (TrackInput t : wanted) {
            if (t.number() < 1 || !numbers.add(t.number())) {
                throw bad("track numbers must be positive and unique");
            }
            required(t.title(), "track title");
            if (t.lengthSeconds() != null && t.lengthSeconds() < 1) {
                throw bad("a track length must be positive");
            }
        }

        AlbumView before = catalogue.album(albumId);
        Map<Integer, Track> existing = new HashMap<>();
        album.getTracks().forEach(t -> existing.put(t.getNumber(), t));
        album.getTracks().removeIf(t -> !numbers.contains(t.getNumber()));
        entities.flush();
        for (TrackInput t : wanted) {
            Track track = existing.get(t.number());
            if (track != null) {
                track.update(t.title().strip(), t.lengthSeconds());
            } else {
                album.addTrack(t.number(), t.title().strip(), t.lengthSeconds(), List.of());
            }
        }
        touched(albumId);
        AlbumView after = fresh(albumId);
        log.record(editor, "TRACKS_UPDATE", after.title(), after.credit().getFirst().artistId(), albumId, before,
                after);
    }

    @Transactional
    public Created addLink(long albumId, LinkInput input, String email) {
        Editor editor = editor(email);
        Album album = managedAlbum(albumId, editor);
        Store store = store(input.store());
        LinkDetails details = details(input, null);

        AlbumView before = catalogue.album(albumId);
        Link link;
        if (input.trackNumber() == null) {
            link = album.addLink(store, details);
        } else {
            Track track = album.getTracks().stream().filter(t -> t.getNumber() == input.trackNumber()).findFirst()
                    .orElseThrow(() -> bad("the album has no track " + input.trackNumber()));
            link = track.addLink(store, details);
        }
        entities.flush();
        long id = link.getId();
        touched(albumId);
        AlbumView after = fresh(albumId);
        log.record(editor, "LINK_ADD", after.title(), after.credit().getFirst().artistId(), albumId, before, after);
        return new Created(id);
    }

    @Transactional
    public void updateLink(long linkId, LinkInput input, String email) {
        Editor editor = editor(email);
        Link link = links.findById(linkId).orElseThrow(() -> notFound("link"));
        long albumId = managedAlbum(link.owningAlbum().getId(), editor).getId();
        Store store = store(input.store());
        LinkDetails details = details(input, link.getEmbedCode());

        AlbumView before = catalogue.album(albumId);
        link.update(store, details);
        touched(albumId);
        AlbumView after = fresh(albumId);
        log.record(editor, "LINK_UPDATE", after.title(), after.credit().getFirst().artistId(), albumId, before, after);
    }

    @Transactional
    public void deleteLink(long linkId, String email) {
        Editor editor = editor(email);
        Link link = links.findById(linkId).orElseThrow(() -> notFound("link"));
        Album album = managedAlbum(link.owningAlbum().getId(), editor);
        long albumId = album.getId();

        AlbumView before = catalogue.album(albumId);
        album.getLinks().remove(link);
        album.getTracks().forEach(t -> t.getLinks().remove(link));
        touched(albumId);
        AlbumView after = fresh(albumId);
        log.record(editor, "LINK_DELETE", after.title(), after.credit().getFirst().artistId(), albumId, before, after);
    }

    /**
     * Выяву выканаўцы загружае толькі ягоны ўліковы запіс; адміністратар можа толькі зьняць яе. Пакуль выявы няма,
     * плітка выканаўцы паказвае вокладку ягонага апошняга альбома.
     */
    @Transactional
    public CoverUrls uploadArtistImage(long artistId, byte[] file, String email) throws IOException {
        Editor editor = editor(email);
        Artist artist = artists.findById(artistId).orElseThrow(() -> notFound("artist"));
        if (!editor.artists().contains(artistId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the artist's own account uploads its image");
        }
        CoverUrls urls = covers.uploadArtistImage(artistId, file, email);
        log.record(editor, "ARTIST_IMAGE_UPLOAD", artist.getName(), artistId, null, null, urls);
        return urls;
    }

    @Transactional
    public void removeArtistImage(long artistId, String email) throws IOException {
        Editor editor = editor(email);
        Artist artist = artists.findById(artistId).orElseThrow(() -> notFound("artist"));
        require(editor.manages(artistId));
        CoverUrls before = covers.artistImage(artistId);
        if (before == null) {
            return;
        }
        covers.removeArtistImage(artistId);
        log.record(editor, "ARTIST_IMAGE_REMOVE", artist.getName(), artistId, null, before, null);
    }

    /** Вокладка, якую выканаўца загружае сам. Адміністратар загружае праз сваю панэль, дзе выбірае падставу. */
    @Transactional
    public CoverUrls uploadCover(long albumId, byte[] file, String email) throws IOException {
        Editor editor = editor(email);
        Album album = managedAlbum(albumId, editor);
        long artistId = album.getCredits().stream().map(c -> c.getArtist().getId())
                .filter(editor.artists()::contains).findFirst()
                .orElseThrow(() -> bad("the admin uploads covers with a basis, on the admin panel"));
        CoverUrls urls = covers.uploadByArtist(albumId, artistId, file, email);
        touched(albumId);
        log.record(editor, "COVER_UPLOAD", album.getTitle(), artistId, albumId, null, urls);
        return urls;
    }

    @Transactional
    public void removeCover(long albumId, String email) throws IOException {
        Editor editor = editor(email);
        Album album = managedAlbum(albumId, editor);
        AlbumView before = catalogue.album(albumId);
        covers.remove(albumId);
        touched(albumId);
        log.record(editor, "COVER_REMOVE", album.getTitle(), before.credit().getFirst().artistId(), albumId,
                before.coverUrl(), null);
    }

    private Editor editor(String email) {
        AppUser user = users.findByEmail(AppUser.normalise(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new Editor(user.getId(), user.getEmail(), user.getRole().isAdmin(),
                team.managedArtists(user.getEmail()));
    }

    private Album managedAlbum(long albumId, Editor editor) {
        Album album = albums.findById(albumId).orElseThrow(() -> notFound("album"));
        require(editor.managesAny(album.getCredits().stream().map(c -> c.getArtist().getId()).toList()));
        return album;
    }

    /** Імёны ў крэдыце — як у файле каталёга: вядомы выканаўца знаходзіцца па імені, новы ствараецца. */
    private List<Credit> credit(List<CreditInput> input) {
        if (input == null || input.isEmpty()) {
            throw bad("an album needs at least one artist");
        }
        List<Credit> credit = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CreditInput c : input) {
            String name = required(c.artist(), "artist name");
            Artist artist = artists.findFirstByName(name).orElseGet(() -> artists.save(new Artist(name)));
            if (!seen.add(artist.getId())) {
                throw bad(name + " is credited twice");
            }
            credit.add(new Credit(artist, c.join()));
        }
        return credit;
    }

    private Store store(String name) {
        String n = required(name, "store");
        return stores.findByName(n).orElseGet(() -> stores.save(new Store(n, null)));
    }

    private static LinkDetails details(LinkInput input, String embedCode) {
        String url = required(input.url(), "url");
        if (!url.matches("https?://\\S+")) {
            throw bad("the link must start with https:// or http://");
        }
        if (input.priceType() == null) {
            throw bad("price type is required");
        }
        if (input.minPrice() != null && input.minPrice().signum() < 0) {
            throw bad("a price cannot be negative");
        }
        String currency = blankToNull(input.currency());
        if (currency != null) {
            currency = currency.toUpperCase(Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) {
                throw bad("a currency is three letters, like EUR");
            }
        }
        return new LinkDetails(url, input.priceType(), input.minPrice(), currency, blankToNull(input.note()),
                embedCode);
    }

    private boolean uses(String table, long artistId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE artist_id = ?)").param(artistId)
                .query(Boolean.class).single();
    }

    private void touched(long albumId) {
        entities.flush();
        jdbc.sql("UPDATE album SET updated_at = now() WHERE id = ?").param(albumId).update();
    }

    /** Стан альбома пасьля зьменаў — з базы, а не з кэшу JPA. */
    private AlbumView fresh(long albumId) {
        entities.flush();
        entities.clear();
        return catalogue.album(albumId);
    }

    private List<ContactState> contacts(long artistId) {
        return jdbc.sql("SELECT kind, value FROM artist_contact WHERE artist_id = ? ORDER BY position")
                .param(artistId)
                .query((rs, n) -> new ContactState(ContactKind.valueOf(rs.getString(1)), rs.getString(2)))
                .list();
    }

    private static ContactState contact(ContactInput input) {
        if (input == null || input.kind() == null) {
            throw bad("a contact needs a kind");
        }
        String value = required(input.value(), "contact");
        if (value.codePointCount(0, value.length()) > MAX_CONTACT_LENGTH) {
            throw bad("a contact is at most " + MAX_CONTACT_LENGTH + " characters");
        }
        if (!input.kind().accepts(value)) {
            throw bad(value + " is not a valid " + input.kind().name().toLowerCase(Locale.ROOT) + " contact");
        }
        return new ContactState(input.kind(), value);
    }

    private static ArtistState state(Artist artist) {
        return new ArtistState(artist.getName(), artist.getAliases().stream().map(ArtistAlias::getName).toList());
    }

    private static int year(Integer year) {
        if (year == null || year < 1900 || year > 2100) {
            throw bad("a year between 1900 and 2100 is required");
        }
        return year;
    }

    private static String required(String value, String what) {
        String v = blankToNull(value);
        if (v == null) {
            throw bad(what + " is required");
        }
        return v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static void require(boolean allowed) {
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you do not manage this artist");
        }
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }
}
