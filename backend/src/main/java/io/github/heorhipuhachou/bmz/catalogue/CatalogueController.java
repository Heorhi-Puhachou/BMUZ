package io.github.heorhipuhachou.bmz.catalogue;

import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.AlbumView;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.ArtistTile;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.ArtistView;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.PageView;
import io.github.heorhipuhachou.bmz.catalogue.CatalogueViews.SearchResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class CatalogueController {

    private final CatalogueService catalogue;

    public CatalogueController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/artists")
    public PageView<ArtistTile> artists(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return catalogue.listArtists(page, size);
    }

    @GetMapping("/artists/{id}")
    public ArtistView artist(@PathVariable long id) {
        return catalogue.artist(id);
    }

    @GetMapping("/albums/{id}")
    public AlbumView album(@PathVariable long id) {
        return catalogue.album(id);
    }

    @GetMapping("/search")
    public SearchResult search(@RequestParam("q") String query) {
        return catalogue.search(query);
    }
}
