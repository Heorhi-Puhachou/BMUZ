package io.github.heorhipuhachou.bmuz.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Рэгістрацыя па пошце. Адказ аднолькавы, ці ёсьць ужо такі адрас, ці не, каб форма не выдавала чужых адрасоў.
 * Уліковы запіс пачынае працаваць толькі пасьля спасылкі з ліста; у базе захоўваецца толькі хэш токена.
 */
@Service
public class RegistrationService {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    /** Новы ліст на той самы непацьверджаны адрас — не часьцей, каб формай нельга было засыпаць чужую пошту. */
    static final Duration RESEND_PAUSE = Duration.ofMinutes(10);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final RegistrationAlarm alarm;
    private final boolean enabled;
    private final String siteUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RegistrationService(AppUserRepository users, PasswordEncoder encoder, Mailer mailer, RegistrationAlarm alarm,
                        @Value("${bmuz.registration.enabled:false}") boolean enabled,
                        @Value("${bmuz.site-url:http://localhost:5173}") String siteUrl) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
        this.alarm = alarm;
        this.enabled = enabled;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.clock = Clock.systemUTC();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public void register(String email, String password) {
        requireEnabled();
        String normalised = AppUser.normalise(email);
        if (!normalised.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not an email address");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "the password needs at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        AppUser existing = users.findByEmail(normalised).orElse(null);
        if (existing != null && existing.isConfirmed()) {
            return;
        }
        Instant now = clock.instant();
        if (existing != null && existing.confirmExpiresAfter(now.plus(TOKEN_LIFETIME).minus(RESEND_PAUSE))) {
            return;
        }
        if (existing != null) {
            users.delete(existing);
            users.flush();
        }
        String token = newToken();
        users.save(AppUser.unconfirmed(normalised, encoder.encode(password), hash(token), now.plus(TOKEN_LIFETIME)));
        mailer.send(normalised, "BMUZ: пацьвердзіце адрас",
                "Каб скончыць рэгістрацыю на " + siteUrl + ", адкрыйце спасылку на працягу 24 гадзін:\n"
                        + siteUrl + "/confirm?token=" + token + "\n\n"
                        + "Калі вы не рэгістраваліся, нічога не рабіце: уліковы запіс не запрацуе.");
        alarm.registered();
    }

    @Transactional
    public boolean confirm(String token) {
        requireEnabled();
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        return users.findByConfirmTokenHash(hash(token)).map(u -> u.confirm(now)).orElse(false);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
