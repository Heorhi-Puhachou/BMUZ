package io.github.heorhipuhachou.bmuz.editing;

import io.github.heorhipuhachou.bmuz.cover.CoverService.CoverUrls;
import io.github.heorhipuhachou.bmuz.editing.CatalogueEditor.AlbumInput;
import io.github.heorhipuhachou.bmuz.editing.CatalogueEditor.ArtistInput;
import io.github.heorhipuhachou.bmuz.editing.CatalogueEditor.ContactsInput;
import io.github.heorhipuhachou.bmuz.editing.CatalogueEditor.Created;
import io.github.heorhipuhachou.bmuz.editing.CatalogueEditor.LinkInput;
import io.github.heorhipuhachou.bmuz.editing.CatalogueEditor.TracksInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Праўка каталёга. Пускае толькі тых, хто ўвайшоў; каго з выканаўцаў можна праўляць, вырашае {@link CatalogueEditor}. */
@RestController
@Validated
class EditController {

    private final CatalogueEditor editor;
    private final EditLog log;

    EditController(CatalogueEditor editor, EditLog log) {
        this.editor = editor;
        this.log = log;
    }

    @GetMapping("/api/edit/stores")
    List<String> stores() {
        return editor.storeNames();
    }

    @PutMapping("/api/edit/artists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateArtist(@PathVariable long id, @RequestBody ArtistInput input, Authentication auth) {
        editor.updateArtist(id, input, auth.getName());
    }

    @PutMapping("/api/edit/artists/{id}/contacts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void replaceContacts(@PathVariable long id, @RequestBody ContactsInput input, Authentication auth) {
        editor.replaceContacts(id, input, auth.getName());
    }

    /** Толькі для адміністратара (/api/admin/**): выдаліць можна, напісаць — не. */
    @DeleteMapping("/api/admin/artist-contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeContact(@PathVariable long id, Authentication auth) {
        editor.removeContact(id, auth.getName());
    }

    /** Толькі для супэрадміністратара (/api/super/**). */
    @DeleteMapping("/api/super/artists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteArtist(@PathVariable long id, Authentication auth) {
        editor.deleteArtist(id, auth.getName());
    }

    @PostMapping("/api/edit/albums")
    @ResponseStatus(HttpStatus.CREATED)
    Created createAlbum(@RequestBody AlbumInput input, Authentication auth) {
        return editor.createAlbum(input, auth.getName());
    }

    @PutMapping("/api/edit/albums/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateAlbum(@PathVariable long id, @RequestBody AlbumInput input, Authentication auth) {
        editor.updateAlbum(id, input, auth.getName());
    }

    @DeleteMapping("/api/edit/albums/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAlbum(@PathVariable long id, Authentication auth) throws IOException {
        editor.deleteAlbum(id, auth.getName());
    }

    @PutMapping("/api/edit/albums/{id}/tracks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void replaceTracks(@PathVariable long id, @RequestBody TracksInput input, Authentication auth) {
        editor.replaceTracks(id, input, auth.getName());
    }

    @PostMapping("/api/edit/albums/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    Created addLink(@PathVariable long id, @RequestBody LinkInput input, Authentication auth) {
        return editor.addLink(id, input, auth.getName());
    }

    @PutMapping("/api/edit/links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateLink(@PathVariable long id, @RequestBody LinkInput input, Authentication auth) {
        editor.updateLink(id, input, auth.getName());
    }

    @DeleteMapping("/api/edit/links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLink(@PathVariable long id, Authentication auth) {
        editor.deleteLink(id, auth.getName());
    }

    @PutMapping("/api/edit/artists/{id}/image")
    CoverUrls uploadArtistImage(@PathVariable long id, @RequestParam MultipartFile file, Authentication auth)
            throws IOException {
        return editor.uploadArtistImage(id, file.getBytes(), auth.getName());
    }

    /** Свой выканаўца — або адміністратар. */
    @DeleteMapping("/api/edit/artists/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeArtistImage(@PathVariable long id, Authentication auth) throws IOException {
        editor.removeArtistImage(id, auth.getName());
    }

    @PutMapping("/api/edit/albums/{id}/cover")
    CoverUrls uploadCover(@PathVariable long id, @RequestParam MultipartFile file, Authentication auth)
            throws IOException {
        return editor.uploadCover(id, file.getBytes(), auth.getName());
    }

    @DeleteMapping("/api/edit/albums/{id}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeCover(@PathVariable long id, Authentication auth) throws IOException {
        editor.removeCover(id, auth.getName());
    }

    /** Апошнія зьмены — толькі для адміністратара (/api/admin/**). */
    @GetMapping("/api/admin/edits")
    List<EditLog.Entry> edits(@RequestParam(defaultValue = "0") @Min(0) int page,
                              @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return log.latest(page, size);
    }
}
