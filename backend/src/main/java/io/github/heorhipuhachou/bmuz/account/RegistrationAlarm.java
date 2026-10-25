package io.github.heorhipuhachou.bmuz.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Калі за гадзіну зьявілася больш за {@value #THRESHOLD} уліковых запісаў, магчыма, рэгіструюцца робаты: усе
 * адміністратары атрымліваюць ліст. Не часьцей за раз на гадзіну, каб сама трывога не засыпала пошту.
 */
@Component
class RegistrationAlarm {

    private static final Logger log = LoggerFactory.getLogger(RegistrationAlarm.class);

    static final int THRESHOLD = 10;
    static final Duration WINDOW = Duration.ofHours(1);

    private final JdbcClient jdbc;
    private final Mailer mailer;
    private final String siteUrl;
    private final Clock clock = Clock.systemUTC();
    private Instant lastSent;

    RegistrationAlarm(JdbcClient jdbc, Mailer mailer,
                      @Value("${bmuz.site-url:http://localhost:5173}") String siteUrl) {
        this.jdbc = jdbc;
        this.mailer = mailer;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    /** Пасьля кожнага новага запісу. Ліст, які не пайшоў, не спыняе рэгістрацыю — толькі пішацца ў журнал. */
    synchronized void registered() {
        Instant now = clock.instant();
        if (lastSent != null && now.isBefore(lastSent.plus(WINDOW))) {
            return;
        }
        long count = jdbc.sql("SELECT count(*) FROM app_user WHERE created_at > now() - make_interval(secs => ?)")
                .param(WINDOW.toSeconds()).query(Long.class).single();
        if (count <= THRESHOLD) {
            return;
        }
        lastSent = now;
        String text = "За апошнюю гадзіну на " + siteUrl + " зьявілася ўліковых запісаў: " + count
                + " (мяжа — " + THRESHOLD + "). Магчыма, рэгіструюцца робаты.\n\n"
                + "Праверце новыя запісы. Каб спыніць рэгістрацыю, выключыце bmuz.registration.enabled.\n\n"
                + "Наступны такі ліст прыйдзе не раней чым праз гадзіну.";
        for (String admin : jdbc.sql("""
                        SELECT email FROM app_user
                        WHERE role IN ('ADMIN', 'SUPER_ADMIN') AND confirmed_at IS NOT NULL ORDER BY id""")
                .query(String.class).list()) {
            try {
                mailer.send(admin, "BMUZ: шмат рэгістрацый за гадзіну", text);
            } catch (RuntimeException e) {
                log.error("Registration alarm to {} failed", admin, e);
            }
        }
    }

    /** Для тэстаў. */
    synchronized void clear() {
        lastSent = null;
    }
}
