package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.catalogue.Album;
import io.github.heorhipuhachou.bmz.catalogue.Artist;
import io.github.heorhipuhachou.bmz.catalogue.ArtistAlias;
import io.github.heorhipuhachou.bmz.catalogue.ArtistRepository;
import io.github.heorhipuhachou.bmz.catalogue.Link;
import io.github.heorhipuhachou.bmz.catalogue.LinkDetails;
import io.github.heorhipuhachou.bmz.catalogue.Store;
import io.github.heorhipuhachou.bmz.catalogue.StoreRepository;
import io.github.heorhipuhachou.bmz.catalogue.Track;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.StoreEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.TrackEntry;
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
import java.util.function.Consumer;

/**
 * Дадае файл каталёгу ў базу. Запісы супастаўляюцца па натуральных ключах — выканаўца па імені, альбом па годзе й
 * назьве, трэк па нумары, спасылка па URL, — таму паўторны імпарт таго самага файлу нічога не дадае.
 */
@Service
public class CatalogueImporter {

    public record Result(int artists, int albums, int tracks, int links) {
    }

    private final ArtistRepository artists;
    private final StoreRepository stores;
    private final JsonMapper json;

    public CatalogueImporter(ArtistRepository artists, StoreRepository stores, JsonMapper json) {
        this.artists = artists;
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
        Counter counter = new Counter();
        Map<String, Store> storeCache = new HashMap<>();
        for (StoreEntry entry : catalogue.stores()) {
            storeCache.put(entry.name(), findOrCreateStore(entry.name(), entry.homepage()));
        }

        for (ArtistEntry artistEntry : catalogue.artists()) {
            Artist artist = artists.findFirstByName(artistEntry.name()).orElse(null);
            if (artist == null) {
                artist = artists.save(new Artist(artistEntry.name()));
                counter.artists++;
            }
            List<String> knownAliases = artist.getAliases().stream().map(ArtistAlias::getName).toList();
            for (String alias : artistEntry.aliases()) {
                if (!knownAliases.contains(alias)) {
                    artist.addAlias(alias);
                }
            }

            for (AlbumEntry albumEntry : artistEntry.albums()) {
                Album album = artist.getAlbums().stream()
                        .filter(a -> a.getYear() == albumEntry.year() && a.getTitle().equals(albumEntry.title()))
                        .findFirst().orElse(null);
                if (album == null) {
                    album = artist.addAlbum(albumEntry.year(), albumEntry.title());
                    counter.albums++;
                }
                Album target = album;
                mergeLinks(albumEntry.links(), album.getLinks(), storeCache, counter,
                        (store, details) -> target.addLink(store, details));

                for (TrackEntry trackEntry : albumEntry.tracks()) {
                    Track track = album.getTracks().stream()
                            .filter(t -> t.getNumber() == trackEntry.number())
                            .findFirst().orElse(null);
                    if (track == null) {
                        track = album.addTrack(trackEntry.number(), trackEntry.title(), trackEntry.lengthSeconds());
                        counter.tracks++;
                    }
                    Track trackTarget = track;
                    mergeLinks(trackEntry.links(), track.getLinks(), storeCache, counter,
                            (store, details) -> trackTarget.addLink(store, details));
                }
            }
        }
        return new Result(counter.artists, counter.albums, counter.tracks, counter.links);
    }

    private interface LinkAdder {
        void add(Store store, LinkDetails details);
    }

    private void mergeLinks(List<LinkEntry> entries, List<Link> existing, Map<String, Store> storeCache,
                            Counter counter, LinkAdder adder) {
        List<String> knownUrls = existing.stream().map(Link::getUrl).toList();
        for (LinkEntry entry : entries) {
            if (knownUrls.contains(entry.url())) {
                continue;
            }
            Store store = storeCache.computeIfAbsent(entry.store(), name -> findOrCreateStore(name, null));
            adder.add(store, new LinkDetails(entry.url(), entry.priceType(), entry.minPrice(), entry.currency(),
                    entry.note(), entry.embedCode()));
            counter.links++;
        }
    }

    private Store findOrCreateStore(String name, String homepage) {
        return stores.findByName(name).orElseGet(() -> stores.save(new Store(name, homepage)));
    }

    private static final class Counter {
        int artists;
        int albums;
        int tracks;
        int links;
    }
}
