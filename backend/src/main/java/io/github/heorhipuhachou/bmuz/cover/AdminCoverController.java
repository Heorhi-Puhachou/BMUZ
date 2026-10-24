package io.github.heorhipuhachou.bmuz.cover;

import io.github.heorhipuhachou.bmuz.cover.CoverService.CoverUrls;
import io.github.heorhipuhachou.bmuz.cover.CoverService.PermissionInput;
import io.github.heorhipuhachou.bmuz.cover.CoverService.PermissionView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Толькі для адміністратара (гл. SecurityConfig: /api/admin/** патрабуе ролі ADMIN). */
@RestController
@RequestMapping("/api/admin")
class AdminCoverController {

    private final CoverService service;

    AdminCoverController(CoverService service) {
        this.service = service;
    }

    @GetMapping("/artists/{artistId}/permissions")
    List<PermissionView> permissions(@PathVariable long artistId) {
        return service.permissionsOf(artistId);
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    PermissionView addPermission(@RequestBody PermissionInput input, Authentication auth) {
        return service.addPermission(input, auth.getName());
    }

    @PutMapping("/albums/{albumId}/cover")
    CoverUrls upload(@PathVariable long albumId, @RequestParam long permissionId,
                     @RequestParam MultipartFile file, Authentication auth) throws IOException {
        return service.upload(albumId, permissionId, file.getBytes(), auth.getName());
    }

    @DeleteMapping("/albums/{albumId}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long albumId) throws IOException {
        service.remove(albumId);
    }
}
