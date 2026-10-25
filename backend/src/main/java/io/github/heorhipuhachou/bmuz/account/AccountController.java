package io.github.heorhipuhachou.bmuz.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Свой уліковы запіс таго, хто ўвайшоў. */
@RestController
@RequestMapping("/api/account")
@ConditionalOnWebApplication
class AccountController {

    record EmailChangeRequest(String password, String email) {
    }

    /** pending — новы адрас, які чакае спасылкі з ліста, або null. */
    record EmailState(String email, String pending) {
    }

    record DisplayNameRequest(String displayName) {
    }

    record SpellingRequest(String spelling) {
    }

    private final EmailChangeService emailChange;
    private final ProfileService profile;

    AccountController(EmailChangeService emailChange, ProfileService profile) {
        this.emailChange = emailChange;
        this.profile = profile;
    }

    /** Імя і правапіс вяртае /api/auth/me. */
    @PutMapping("/display-name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rename(@RequestBody DisplayNameRequest body, Authentication auth) {
        profile.rename(auth.getName(), body.displayName());
    }

    @PutMapping("/spelling")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void chooseSpelling(@RequestBody SpellingRequest body, Authentication auth) {
        profile.chooseSpelling(auth.getName(), body.spelling());
    }

    @GetMapping("/email")
    EmailState email(Authentication auth) {
        return new EmailState(auth.getName(), emailChange.pending(auth.getName()));
    }

    /** Адказ 202, ці заняты новы адрас, ці не. */
    @PostMapping("/email")
    ResponseEntity<Void> changeEmail(@RequestBody EmailChangeRequest body, Authentication auth) {
        emailChange.request(auth.getName(), body.password(), body.email());
        return ResponseEntity.accepted().build();
    }
}
