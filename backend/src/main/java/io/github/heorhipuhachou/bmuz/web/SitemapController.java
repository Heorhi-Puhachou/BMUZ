package io.github.heorhipuhachou.bmuz.web;

import io.github.heorhipuhachou.bmuz.catalogue.AlbumRepository;
import io.github.heorhipuhachou.bmuz.catalogue.ArtistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Сьпіс старонак для пошукавікоў (sitemap.xml) і правілы для іх (robots.txt). Адрас сайта бярэцца з налад, як у
 * лістах: запыт праходзіць праз CloudFront, і ягоны адрас — не той, што бачыць наведнік.
 */
@RestController
class SitemapController {

    /** Старонкі ўліковага запісу, тыя ж, што ў PageController.account(): пустыя без уваходу, шукаць у іх няма чаго. */
    private static final String[] PRIVATE_PAGES = {"/login", "/admin", "/register", "/confirm", "/forgot", "/reset",
            "/invite", "/edits", "/team", "/account", "/email"};

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final String siteUrl;

    SitemapController(ArtistRepository artists, AlbumRepository albums,
                      @Value("${bmuz.site-url:http://localhost:5173}") String siteUrl) {
        this.artists = artists;
        this.albums = albums;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    @GetMapping("/sitemap.xml")
    @Transactional(readOnly = true)
    ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(xml, "/");
        url(xml, "/about");
        artists.findAllIds().forEach(id -> url(xml, "/artists/" + id));
        albums.findAllIds().forEach(id -> url(xml, "/albums/" + id));
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8))
                .body(xml.toString());
    }

    @GetMapping("/robots.txt")
    ResponseEntity<String> robots() {
        StringBuilder txt = new StringBuilder("User-agent: *\n");
        for (String page : PRIVATE_PAGES) {
            txt.append("Disallow: ").append(page).append('\n');
        }
        txt.append("\nSitemap: ").append(siteUrl).append("/sitemap.xml\n");
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(txt.toString());
    }

    private void url(StringBuilder xml, String path) {
        xml.append("  <url><loc>").append(siteUrl).append(path).append("</loc></url>\n");
    }
}
