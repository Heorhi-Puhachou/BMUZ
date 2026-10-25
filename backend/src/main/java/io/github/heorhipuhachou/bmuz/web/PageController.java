package io.github.heorhipuhachou.bmuz.web;

import io.github.heorhipuhachou.bmuz.catalogue.CatalogueService;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.AlbumView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.ArtistView;
import io.github.heorhipuhachou.bmuz.catalogue.CatalogueViews.CreditView;
import io.github.heorhipuhachou.bmuz.catalogue.ListFilter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Аддае старонкі сайта. Старонкі будуюцца ў браўзэры, але загаловак і апісаньне кожнай старонкі сэрвэр упісвае ў
 * HTML сам: спасылку, устаўленую ў Telegram ці Facebook, яны чытаюць без JavaScript.
 */
@Controller
class PageController {

    private static final String SITE = "Беларуская МУЗыка";
    private static final String SITE_DESCRIPTION = "Беларуская музыка, якую можна спампаваць легальна.";
    private static final String TITLE_TAG = "<title>" + SITE + "</title>";

    private final CatalogueService catalogue;
    private final String template;

    PageController(CatalogueService catalogue) throws IOException {
        this.catalogue = catalogue;
        this.template = readTemplate();
    }

    private static String readTemplate() throws IOException {
        ClassPathResource index = new ClassPathResource("static/index.html");
        if (!index.exists()) {
            return null;
        }
        try (InputStream in = index.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping(value = {"/", "/search"}, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> home() {
        return page(HttpStatus.OK, SITE, SITE_DESCRIPTION);
    }

    @GetMapping(value = "/about", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> about() {
        return page(HttpStatus.OK, "Пра сайт — " + SITE, SITE_DESCRIPTION);
    }

    /** Старонкі ўліковага запісу. Іх адкрываюць наўпрост — у тым ліку спасылкай з ліста, — таму сэрвэр мусіць іх аддаваць. */
    @GetMapping(value = {"/login", "/admin", "/register", "/confirm", "/forgot", "/reset", "/invite", "/edits", "/team",
            "/checks", "/account", "/email"},
            produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> account() {
        return page(HttpStatus.OK, SITE, SITE_DESCRIPTION);
    }

    @GetMapping(value = "/artists/{id}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> artist(@PathVariable long id) {
        ArtistView artist;
        try {
            artist = catalogue.artist(id, ListFilter.NONE);
        } catch (ResponseStatusException e) {
            return page(HttpStatus.NOT_FOUND, SITE, SITE_DESCRIPTION);
        }
        return page(HttpStatus.OK, artist.name() + " — " + SITE,
                artist.name() + ": альбомы, якія можна спампаваць легальна.");
    }

    @GetMapping(value = "/albums/{id}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> album(@PathVariable long id) {
        AlbumView album;
        try {
            album = catalogue.album(id);
        } catch (ResponseStatusException e) {
            return page(HttpStatus.NOT_FOUND, SITE, SITE_DESCRIPTION);
        }
        String credit = credit(album.credit());
        return page(HttpStatus.OK, album.title() + " — " + credit + " — " + SITE,
                credit + ", " + album.year() + ". Дзе спампаваць легальна.");
    }

    private ResponseEntity<String> page(HttpStatus status, String title, String description) {
        if (template == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "frontend is not built into this jar");
        }
        String t = HtmlUtils.htmlEscape(title, StandardCharsets.UTF_8.name());
        String d = HtmlUtils.htmlEscape(description, StandardCharsets.UTF_8.name());
        String head = "<title>" + t + "</title>\n"
                + "    <meta name=\"description\" content=\"" + d + "\" />\n"
                + "    <meta property=\"og:site_name\" content=\"" + SITE + "\" />\n"
                + "    <meta property=\"og:title\" content=\"" + t + "\" />\n"
                + "    <meta property=\"og:description\" content=\"" + d + "\" />\n"
                + "    <meta property=\"og:type\" content=\"website\" />";
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(template.replace(TITLE_TAG, head));
    }

    private static String credit(List<CreditView> credit) {
        return credit.stream().map(c -> c.name() + c.joinPhrase()).collect(Collectors.joining());
    }
}
