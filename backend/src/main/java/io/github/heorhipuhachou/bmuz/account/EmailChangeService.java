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
 * Зьмена адраса пошты. Трэба ведаць пароль — каб чужы, хто сеў за ўвайшоўшы кампутар, не забраў запіс сабе, — і
 * адкрыць спасылку з ліста на новы адрас, каб памылка ў адрасе не адрэзала ўладальніка ад яго запісу. Пакуль спасылка
 * не адкрытая, усё ідзе на стары адрас. Пасьля зьмены стары адрас атрымлівае паведамленьне, а ўсе сэсіі запісу
 * заканчваюцца: увайсьці трэба наноў, ужо з новым адрасам.
 */
@Service
public class EmailChangeService {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    /** Новы ліст на той самы адрас — не часьцей, каб формай нельга было засыпаць чужую пошту. */
    static final Duration RESEND_PAUSE = Duration.ofMinutes(10);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final LoginLimiter limiter;
    private final String siteUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    EmailChangeService(AppUserRepository users, PasswordEncoder encoder, Mailer mailer, LoginLimiter limiter,
                       @Value("${bmuz.site-url:http://localhost:5173}") String siteUrl) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
        this.limiter = limiter;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.clock = Clock.systemUTC();
    }

    /** Новы адрас, які чакае пацьверджаньня, або null. */
    @Transactional(readOnly = true)
    public String pending(String currentEmail) {
        return users.findByEmail(AppUser.normalise(currentEmail)).map(u -> u.pendingEmail(clock.instant()))
                .orElse(null);
    }

    /**
     * Адказ аднолькавы, ці заняты новы адрас, ці не: форма не павінна выдаваць чужых адрасоў. Калі заняты, на яго
     * ідзе ліст без спасылкі. Няправільны пароль лічыцца як няўдалы ўваход і таксама замыкае запіс на 15 хвілін.
     */
    @Transactional
    public void request(String currentEmail, String password, String newEmail) {
        String current = AppUser.normalise(currentEmail);
        if (limiter.isLocked(current)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        AppUser user = users.findByEmail(current)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            limiter.failed(current);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "wrong password");
        }
        String target = AppUser.normalise(newEmail == null ? "" : newEmail);
        if (!target.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not an email address");
        }
        if (target.equals(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "this is already the address");
        }
        Instant now = clock.instant();
        if (user.emailChangeRequestedAfter(target, now.plus(TOKEN_LIFETIME).minus(RESEND_PAUSE))) {
            return;
        }
        if (users.findByEmail(target).filter(AppUser::isConfirmed).isPresent()) {
            user.requestEmailChange(target, null, now.plus(TOKEN_LIFETIME));
            mailer.send(target, "BMUZ: адрас ужо заняты",
                    "Нехта папрасіў перавесьці свой уліковы запіс на " + siteUrl + " на гэты адрас, але ў гэтага "
                            + "адраса ўжо ёсьць уліковы запіс. Таму нічога не зьмянілася.\n\n"
                            + "Калі вы не прасілі, нічога не рабіце.");
            return;
        }
        String token = newToken();
        user.requestEmailChange(target, RegistrationService.hash(token), now.plus(TOKEN_LIFETIME));
        mailer.send(target, "BMUZ: пацьвердзіце новы адрас",
                "Каб уваходзіць на " + siteUrl + " з гэтым адрасам замест " + current
                        + ", адкрыйце спасылку на працягу 24 гадзін:\n"
                        + siteUrl + "/email?token=" + token + "\n\n"
                        + "Калі вы не прасілі, нічога не рабіце: адрас застанецца ранейшы.");
    }

    /**
     * {@code false} — спасылка няправільная, ужо выкарыстаная або састарэла. Адрас, які за гэты час заняў іншы
     * пацьверджаны запіс, адмаўляецца з 409; непацьверджаны запіс на ім выдаляецца: спасылка даказвае, што пошта
     * належыць таму, хто мяняе адрас, а не таму, хто калісьці толькі пачаў рэгістрацыю.
     */
    @Transactional
    public boolean confirm(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        AppUser user = users.findByEmailChangeTokenHash(RegistrationService.hash(token)).orElse(null);
        String target = user == null ? null : user.pendingEmail(clock.instant());
        if (target == null) {
            return false;
        }
        AppUser holder = users.findByEmail(target).orElse(null);
        if (holder != null && holder.isConfirmed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "the address already has an account");
        }
        if (holder != null) {
            users.delete(holder);
            users.flush();
        }
        String old = user.getEmail();
        user.changeEmail();
        users.flush();
        mailer.send(old, "BMUZ: адрас зьменены",
                "Ваш уліковы запіс на " + siteUrl + " цяпер уваходзіць з адрасам " + target
                        + ". На гэты адрас лісты больш не прыйдуць.\n\n"
                        + "Калі вы гэтага не рабілі, напішыце на адрас са старонкі " + siteUrl + "/about");
        return true;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
