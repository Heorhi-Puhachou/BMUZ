package io.github.heorhipuhachou.bmuz.community;

import io.github.heorhipuhachou.bmuz.community.Reviews.AlbumReviews;
import io.github.heorhipuhachou.bmuz.community.Reviews.ReviewInput;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Чытаць водгукі можа кожны; пісаць — той, хто ўвайшоў (/api/account/**), здымаць чужыя — адміністратар
 * (/api/admin/**). Абодва правілы — у SecurityConfig.
 */
@RestController
class ReviewController {

    private final Reviews reviews;

    ReviewController(Reviews reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/albums/{id}/reviews")
    AlbumReviews of(@PathVariable long id, Authentication auth) {
        boolean visitor = auth == null || auth instanceof AnonymousAuthenticationToken;
        return reviews.of(id, visitor ? null : auth.getName());
    }

    @PutMapping("/api/account/reviews/{albumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void save(@PathVariable long albumId, @RequestBody ReviewInput input, Authentication auth) {
        reviews.save(albumId, input, auth.getName());
    }

    @DeleteMapping("/api/account/reviews/{albumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteOwn(@PathVariable long albumId, Authentication auth) {
        reviews.deleteOwn(albumId, auth.getName());
    }

    @DeleteMapping("/api/admin/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long id) {
        reviews.remove(id);
    }
}
