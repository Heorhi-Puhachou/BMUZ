package io.github.heorhipuhachou.bmuz.catalogue;

import io.github.heorhipuhachou.bmuz.account.AppUser;
import io.github.heorhipuhachou.bmuz.account.AppUserRepository;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.AlbumView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.ArtistTile;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.ArtistView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.PageView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.SearchResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** unchecked=true — фільтр адміністратара: толькі альбомы, якіх ён не праглядзеў або што зьмянілі пасьля прагляду. */
@RestController
@RequestMapping("/api")
@Validated
public class CatalogueController {

    private final CatalogueService catalogue;
    private final AppUserRepository users;

    public CatalogueController(CatalogueService catalogue, AppUserRepository users) {
        this.catalogue = catalogue;
        this.users = users;
    }

    @GetMapping("/artists")
    public PageView<ArtistTile> artists(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                                           @RequestParam(defaultValue = "false") boolean free,
                                           @RequestParam(defaultValue = "false") boolean unchecked,
                                           Authentication auth) {
        return catalogue.listArtists(page, size, filter(free, unchecked, auth));
    }

    @GetMapping("/artists/{id}")
    public ArtistView artist(@PathVariable long id, @RequestParam(defaultValue = "false") boolean free,
                             @RequestParam(defaultValue = "false") boolean unchecked, Authentication auth) {
        return catalogue.artist(id, filter(free, unchecked, auth));
    }

    @GetMapping("/albums/{id}")
    public AlbumView album(@PathVariable long id) {
        return catalogue.album(id);
    }

    @GetMapping("/search")
    public SearchResult search(@RequestParam("q") String query,
                               @RequestParam(defaultValue = "false") boolean free,
                               @RequestParam(defaultValue = "false") boolean unchecked, Authentication auth) {
        return catalogue.search(query, filter(free, unchecked, auth));
    }

    private ListFilter filter(boolean free, boolean unchecked, Authentication auth) {
        if (!unchecked) {
            return new ListFilter(free, 0);
        }
        long admin = auth == null ? 0 : users.findByEmail(AppUser.normalise(auth.getName()))
                .filter(u -> u.getRole().isAdmin()).map(AppUser::getId).orElse(0L);
        if (admin == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only an admin filters by checks");
        }
        return new ListFilter(free, admin);
    }
}
