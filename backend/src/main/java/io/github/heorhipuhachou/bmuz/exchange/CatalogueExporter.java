package io.github.heorhipuhachou.bmuz.exchange;

import io.github.heorhipuhachou.bmuz.catalogue.AlbumRepository;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistAlias;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistRepository;
import io.github.heorhipuhachou.bmuz.catalogue.Credit;
import io.github.heorhipuhachou.bmuz.catalogue.Link;
import io.github.heorhipuhachou.bmuz.catalogue.LinkDetails;
import io.github.heorhipuhachou.bmuz.catalogue.StoreRepository;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.LinkEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.StoreEntry;
import io.github.heorhipuhachou.bmuz.exchange.CatalogueFile.TrackEntry;
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
    private final AlbumRepository albums;
    private final StoreRepository stores;
    private final JsonMapper json;

    public CatalogueExporter(ArtistRepository artists, AlbumRepository albums, StoreRepository stores,
                             JsonMapper json) {
        this.artists = artists;
        this.albums = albums;
        this.stores = stores;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public CatalogueFile export() {
        List<StoreEntry> storeEntries = stores.findAll(Sort.by("name")).stream()
                .map(s -> new StoreEntry(s.getName(), s.getHomepage()))
                .toList();
        List<ArtistEntry> artistEntries = artists.findAll(Sort.by("name")).stream()
                .map(a -> new ArtistEntry(a.getName(), a.getAliases().stream().map(ArtistAlias::getName).toList()))
                .toList();
        List<AlbumEntry> albumEntries = albums.findAll(Sort.by("year", "title", "id")).stream()
                .map(album -> new AlbumEntry(album.getYear(), album.getTitle(), credit(album.getCredits()),
                        links(album.getLinks()),
                        album.getTracks().stream()
                                .map(t -> new TrackEntry(t.getNumber(), t.getTitle(), t.getLengthSeconds(),
                                        credit(t.getCredits()), links(t.getLinks())))
                                .toList()))
                .toList();
        return new CatalogueFile(CatalogueFile.FORMAT_VERSION, storeEntries, artistEntries, albumEntries);
    }

    @Transactional(readOnly = true)
    public void exportTo(Path file) {
        json.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build().writeValue(file.toFile(), export());
    }

    private static List<CreditEntry> credit(List<Credit> credits) {
        return credits.stream()
                .map(c -> new CreditEntry(c.getArtist().getName(), c.getJoinPhrase().isEmpty() ? null : c.getJoinPhrase()))
                .toList();
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
