package io.github.heorhipuhachou.bmuz.cover;

import io.github.heorhipuhachou.bmuz.cover.CoverService.CoverUrls;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Толькі для адміністратара (гл. SecurityConfig: /api/admin/** патрабуе ролі ADMIN). */
@RestController
@RequestMapping("/api/admin")
class AdminCoverController {

    private final CoverService service;

    AdminCoverController(CoverService service) {
        this.service = service;
    }

    @PutMapping("/albums/{albumId}/cover")
    CoverUrls upload(@PathVariable long albumId, @RequestParam(required = false) String licence,
                     @RequestParam(required = false) String licenceUrl, @RequestParam MultipartFile file,
                     Authentication auth) throws IOException {
        return service.upload(albumId, licence, licenceUrl, file.getBytes(), auth.getName());
    }

    @DeleteMapping("/albums/{albumId}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long albumId) throws IOException {
        service.remove(albumId);
    }
}
