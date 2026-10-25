package io.github.heorhipuhachou.bmuz.editing;

import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.PageView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Прагляд альбомаў. Толькі для адміністратара (гл. SecurityConfig: /api/admin/** патрабуе ролі ADMIN). */
@RestController
@RequestMapping("/api/admin/albums")
class AlbumCheckController {

    private final AlbumChecks checks;

    AlbumCheckController(AlbumChecks checks) {
        this.checks = checks;
    }

    @GetMapping
    PageView<AlbumChecks.Item> albums(@RequestParam(defaultValue = "false") boolean checked,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                                      Authentication auth) {
        return checks.list(auth.getName(), checked, page, size);
    }

    @GetMapping("/unchecked")
    AlbumChecks.Unchecked unchecked(Authentication auth) {
        return checks.unchecked(auth.getName());
    }

    @GetMapping("/{id}/checks")
    List<AlbumChecks.Check> checks(@PathVariable long id) {
        return checks.checks(id);
    }

    @PutMapping("/{id}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void check(@PathVariable long id, Authentication auth) {
        checks.check(id, auth.getName());
    }

    @DeleteMapping("/{id}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void uncheck(@PathVariable long id, Authentication auth) {
        checks.uncheck(id, auth.getName());
    }
}
