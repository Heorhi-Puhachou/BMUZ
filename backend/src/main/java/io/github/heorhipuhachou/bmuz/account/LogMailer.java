package io.github.heorhipuhachou.bmuz.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Для распрацоўкі і пакуль няма bmuz.mail.from: ліст не адпраўляецца, а пішацца ў журнал бэкэнда. */
@Component
@ConditionalOnExpression("'${bmuz.mail.from:}'.isBlank()")
class LogMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LogMailer.class);

    @Override
    public void send(String to, String subject, String text) {
        log.info("MAIL (not sent) to {}: {}\n{}", to, subject, text);
    }
}
