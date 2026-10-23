package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.catalogue.ArtistAlias;
import io.github.heorhipuhachou.bmz.catalogue.ArtistRepository;
import io.github.heorhipuhachou.bmz.catalogue.Link;
import io.github.heorhipuhachou.bmz.catalogue.LinkDetails;
import io.github.heorhipuhachou.bmz.catalogue.StoreRepository;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.StoreEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.TrackEntry;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

@Service
public class CatalogueExporter {

    private final ArtistRepository artists;
    private final StoreRepository stores;
    private final JsonMapper json;

    public CatalogueExporter(ArtistRepository artists, StoreRepository stores, JsonMapper json) {
        this.artists = artists;
        this.stores = stores;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public CatalogueFile export() {
        List<StoreEntry> storeEntries = stores.findAll(Sort.by("name")).stream()
                .map(s -> new StoreEntry(s.getName(), s.getHomepage()))
                .toList();
        List<ArtistEntry> artistEntries = artists.findAll(Sort.by("name")).stream()
                .map(artist -> new ArtistEntry(artist.getName(),
                        artist.getAliases().stream().map(ArtistAlias::getName).toList(),
                        artist.getAlbums().stream()
                                .map(album -> new AlbumEntry(album.getYear(), album.getTitle(),
                                        links(album.getLinks()),
                                        album.getTracks().stream()
                                                .map(t -> new TrackEntry(t.getNumber(), t.getTitle(),
                                                        t.getLengthSeconds(), links(t.getLinks())))
                                                .toList()))
                                .toList()))
                .toList();
        return new CatalogueFile(CatalogueFile.FORMAT_VERSION, storeEntries, artistEntries);
    }

    @Transactional(readOnly = true)
    public void exportTo(Path file) {
        json.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build().writeValue(file.toFile(), export());
    }

    private static List<LinkEntry> links(List<Link> links) {
        return links.stream()
                .map(link -> {
                    LinkDetails d = link.details();
                    return new LinkEntry(link.getStore().getName(), d.url(), d.priceType(), d.minPrice(),
                            d.currency(), d.note(), d.embedCode());
                })
                .toList();
    }
}
