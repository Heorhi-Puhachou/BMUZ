package io.github.heorhipuhachou.bmuz.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Адпраўка праз Amazon SES, калі зададзены bmuz.mail.from. Правы на адпраўку праграма бярэ з ролі сэрвэра. Імя
 * адпраўніка не ASCII, таму ў загалоўку From яно закадаванае (MIME encoded-word), як патрабуе SES.
 */
@Component
@ConditionalOnExpression("!'${bmuz.mail.from:}'.isBlank()")
class SesMailer implements Mailer {

    private static final String SENDER_NAME = "Беларуская МУЗыка";

    private final SesV2Client ses = SesV2Client.create();
    private final String from;

    SesMailer(@Value("${bmuz.mail.from}") String from) {
        this.from = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(SENDER_NAME.getBytes(StandardCharsets.UTF_8))
                + "?= <" + from.strip() + ">";
    }

    @Override
    public void send(String to, String subject, String text) {
        ses.sendEmail(b -> b
                .fromEmailAddress(from)
                .destination(d -> d.toAddresses(to))
                .content(c -> c.simple(m -> m
                        .subject(s -> s.data(subject).charset("UTF-8"))
                        .body(body -> body.text(t -> t.data(text).charset("UTF-8"))))));
    }
}
