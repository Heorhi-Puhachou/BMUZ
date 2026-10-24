package io.github.heorhipuhachou.bmuz.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Аднаўленьне забытага пароля па пошце. Працуе і тады, калі рэгістрацыя выключаная: адміністратару таксама трэба
 * вярнуцца. Адказ аднолькавы для любога адраса, каб форма не выдавала, хто мае ўліковы запіс.
 */
@Service
public class PasswordResetService {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    /** Новы ліст на той самы адрас — не часьцей, каб формай нельга было засыпаць чужую пошту. */
    static final Duration RESEND_PAUSE = Duration.ofMinutes(10);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final LoginLimiter limiter;
    private final String siteUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    PasswordResetService(AppUserRepository users, PasswordEncoder encoder, Mailer mailer, LoginLimiter limiter,
                         @Value("${bmuz.site-url:http://localhost:5173}") String siteUrl) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
        this.limiter = limiter;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.clock = Clock.systemUTC();
    }

    /** Ліст са спасылкай ідзе толькі на пацьверджаны адрас; на любы іншы нічога не адбываецца. */
    @Transactional
    public void request(String email) {
        AppUser user = users.findByEmail(AppUser.normalise(email)).orElse(null);
        if (user == null || !user.isConfirmed()) {
            return;
        }
        Instant now = clock.instant();
        if (user.resetExpiresAfter(now.plus(TOKEN_LIFETIME).minus(RESEND_PAUSE))) {
            return;
        }
        String token = newToken();
        user.requestReset(RegistrationService.hash(token), now.plus(TOKEN_LIFETIME));
        mailer.send(user.getEmail(), "BMUZ: новы пароль",
                "Каб задаць новы пароль на " + siteUrl + ", адкрыйце спасылку на працягу гадзіны:\n"
                        + siteUrl + "/reset?token=" + token + "\n\n"
                        + "Калі вы не прасілі новага пароля, нічога не рабіце: стары застаецца.");
    }

    /** {@code false} — спасылка няправільная, ужо выкарыстаная або састарэла. */
    @Transactional
    public boolean reset(String token, String password) {
        if (password == null || password.length() < RegistrationService.MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "the password needs at least " + RegistrationService.MIN_PASSWORD_LENGTH + " characters");
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        AppUser user = users.findByResetTokenHash(RegistrationService.hash(token)).orElse(null);
        if (user == null || !user.reset(encoder.encode(password), clock.instant())) {
            return false;
        }
        limiter.succeeded(user.getEmail());
        return true;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
