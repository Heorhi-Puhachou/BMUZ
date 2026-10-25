package io.github.heorhipuhachou.bmuz.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Карыстальнік выдаляе свой уліковы запіс сам. Трэба ведаць пароль — як пры зьмене адраса. Разам з запісам сыходзяць
 * адрас, хэш пароля, імя, правапіс, прагляды альбомаў і правы на выканаўцаў. Журнал зьменаў застаецца, але без адраса;
 * загружаныя вокладкі і выявы застаюцца без сувязі з запісам. Сэсіі на іншых прыладах канчае
 * {@link FreshRolesFilter}: запісу больш няма. Суперадміністратара выдаліць нельга — сайт застаўся б без яго.
 */
@Service
class AccountDeletionService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final LoginLimiter limiter;
    private final Mailer mailer;
    private final JdbcClient jdbc;
    private final String siteUrl;

    AccountDeletionService(AppUserRepository users, PasswordEncoder encoder, LoginLimiter limiter, Mailer mailer,
                           JdbcClient jdbc, @Value("${bmuz.site-url:http://localhost:5173}") String siteUrl) {
        this.users = users;
        this.encoder = encoder;
        this.limiter = limiter;
        this.mailer = mailer;
        this.jdbc = jdbc;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    /** Няправільны пароль лічыцца як няўдалы ўваход і таксама замыкае запіс на 15 хвілін. */
    @Transactional
    public void delete(String currentEmail, String password) {
        String email = AppUser.normalise(currentEmail);
        if (limiter.isLocked(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            limiter.failed(email);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "wrong password");
        }
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "the super-admin is changed only by the command on the server");
        }
        jdbc.sql("UPDATE edit_log SET email = '' WHERE user_id = ?").param(user.getId()).update();
        users.delete(user);
        users.flush();
        mailer.send(email, "BMUZ: уліковы запіс выдалены",
                "Ваш уліковы запіс на " + siteUrl + " выдалены разам з адрасам пошты, імем і правапісам. "
                        + "Больш лістоў адсюль не прыйдзе.\n\n"
                        + "Калі вы гэтага не рабілі, напішыце на адрас са старонкі " + siteUrl + "/about");
    }
}
