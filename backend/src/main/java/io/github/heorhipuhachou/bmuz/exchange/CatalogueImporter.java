package io.github.heorhipuhachou.bmuz.exchange;

import io.github.heorhipuhachou.bmuz.catalogue.Album;
import io.github.heorhipuhachou.bmuz.catalogue.AlbumRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Artist;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistAlias;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Credit;
import io.github.heorhipuhachou.bmuz.catalogue.Link;
import io.github.heorhipuhachou.bmuz.catalogue.LinkDetails;
import io.github.heorhipuhachou.bmuz.catalogue.Store;
import io.github.heorhipuhachou.bmuz.catalogue.StoreRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Track;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.LinkEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.StoreEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.TrackEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Дадае файл каталёгу ў базу. Запісы супастаўляюцца па натуральных ключах — выканаўца па імені, альбом па годзе,
 * назьве й выканаўцах, трэк па нумары, спасылка па URL, — таму паўторны імпарт таго самага файлу нічога не дадае.
 * Тое, што ўжо ёсьць у базе, імпарт не мяняе і не выдаляе.
 */
@Service
public class CatalogueImporter {

    public record Result(int artists, int albums, int tracks, int links) {
    }

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final StoreRepository stores;
    private final JsonMapper json;

    public CatalogueImporter(ArtistRepository artists, AlbumRepository albums, StoreRepository stores,
                             JsonMapper json) {
        this.artists = artists;
        this.albums = albums;
        this.stores = stores;
        this.json = json;
    }

    @Transactional
    public Result importByTxt(Path file) throws IOException {
        return merge(new ByTxtParser().parse(Files.readAllLines(file, StandardCharsets.UTF_8)));
    }

    @Transactional
    public Result importJson(Path file) {
        CatalogueFile catalogue = json.readValue(file.toFile(), CatalogueFile.class);
        if (catalogue.formatVersion() != CatalogueFile.FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    file + ": format version " + catalogue.formatVersion() + ", expected " + CatalogueFile.FORMAT_VERSION);
        }
        return merge(catalogue);
    }

    @Transactional
    public Result merge(CatalogueFile catalogue) {
        Run run = new Run();
        for (StoreEntry entry : catalogue.stores()) {
            run.stores.put(entry.name(), findOrCreateStore(entry.name(), entry.homepage()));
        }
        for (ArtistEntry entry : catalogue.artists()) {
            Artist artist = run.artist(entry.name());
            List<String> knownAliases = artist.getAliases().stream().map(ArtistAlias::getName).toList();
            for (String alias : entry.aliases()) {
                if (!knownAliases.contains(alias)) {
                    artist.addAlias(alias);
                }
            }
        }
        for (AlbumEntry entry : catalogue.albums()) {
            mergeAlbum(entry, run);
        }
        return new Result(run.artistsAdded, run.albumsAdded, run.tracksAdded, run.linksAdded);
    }

    private void mergeAlbum(AlbumEntry entry, Run run) {
        List<Credit> credits = run.credits(entry.credit());
        List<String> names = entry.credit().stream().map(CreditEntry::artist).toList();
        Album album = albums.findByYearAndTitle(entry.year(), entry.title()).stream()
                .filter(a -> a.getCredits().stream().map(c -> c.getArtist().getName()).toList().equals(names))
                .findFirst().orElse(null);
        if (album == null) {
            album = albums.save(new Album(entry.year(), entry.title(), credits));
            run.albumsAdded++;
        }
        Album target = album;
        mergeLinks(entry.links(), album.getLinks(), run, (store, details) -> target.addLink(store, details));

        for (TrackEntry trackEntry : entry.tracks()) {
            Track track = album.getTracks().stream()
                    .filter(t -> t.getNumber() == trackEntry.number())
                    .findFirst().orElse(null);
            if (track == null) {
                track = album.addTrack(trackEntry.number(), trackEntry.title(), trackEntry.lengthSeconds(),
                        run.credits(trackEntry.credit()));
                run.tracksAdded++;
            }
            Track trackTarget = track;
            mergeLinks(trackEntry.links(), track.getLinks(), run,
                    (store, details) -> trackTarget.addLink(store, details));
        }
    }

    private interface LinkAdder {
        void add(Store store, LinkDetails details);
    }

    private void mergeLinks(List<LinkEntry> entries, List<Link> existing, Run run, LinkAdder adder) {
        List<String> knownUrls = existing.stream().map(Link::getUrl).toList();
        for (LinkEntry entry : entries) {
            if (knownUrls.contains(entry.url())) {
                continue;
            }
            Store store = run.stores.computeIfAbsent(entry.store(), name -> findOrCreateStore(name, null));
            adder.add(store, new LinkDetails(entry.url(), entry.priceType(), entry.minPrice(), entry.currency(),
                    entry.note(), entry.embedCode()));
            run.linksAdded++;
        }
    }

    private Store findOrCreateStore(String name, String homepage) {
        return stores.findByName(name).orElseGet(() -> stores.save(new Store(name, homepage)));
    }

    /** Стан аднаго імпарту: ужо знойдзеныя выканаўцы й крамы і колькасьць дададзенага. */
    private final class Run {
        final Map<String, Artist> artistCache = new HashMap<>();
        final Map<String, Store> stores = new HashMap<>();
        int artistsAdded;
        int albumsAdded;
        int tracksAdded;
        int linksAdded;

        Artist artist(String name) {
            return artistCache.computeIfAbsent(name, n -> artists.findFirstByName(n).orElseGet(() -> {
                artistsAdded++;
                return artists.save(new Artist(n));
            }));
        }

        List<Credit> credits(List<CreditEntry> entries) {
            return entries.stream().map(e -> new Credit(artist(e.artist()), e.join())).toList();
        }
    }
}
