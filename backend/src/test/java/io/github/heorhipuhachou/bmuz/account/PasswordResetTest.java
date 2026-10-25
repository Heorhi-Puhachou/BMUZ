package io.github.heorhipuhachou.bmuz.account;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import io.github.heorhipuhachou.bmuz.account.RegistrationTest.RecordingMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Import({TestcontainersConfiguration.class, RegistrationTest.Mails.class})
@SpringBootTest(properties = "bmuz.site-url=https://bmuz.example")
@AutoConfigureMockMvc
class PasswordResetTest {

    private static final String EMAIL = "admin@example.org";
    private static final String OLD_PASSWORD = "the forgotten password";
    private static final String NEW_PASSWORD = "a brand new password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailer mailer;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void addAdmin(@Autowired AppUserRepository users, @Autowired PasswordEncoder encoder,
                  @Autowired LoginLimiter limiter) {
        jdbc.sql("TRUNCATE app_user RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(EMAIL, encoder.encode(OLD_PASSWORD), Role.ADMIN));
        mailer.sent.clear();
        limiter.clear();
    }

    private int forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private String tokenFromMail() {
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mailer.sent.getLast()[2]);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void setsANewPasswordFromTheMailedLink() throws Exception {
        assertThat(forgot(" Admin@Example.org ")).isEqualTo(202);
        assertThat(mailer.sent).hasSize(1);
        assertThat(mailer.sent.getFirst()[0]).isEqualTo(EMAIL);
        assertThat(mailer.sent.getFirst()[2]).contains("https://bmuz.example/reset?token=");

        assertThat(reset(tokenFromMail(), NEW_PASSWORD)).isEqualTo(204);
        assertThat(login(OLD_PASSWORD)).as("old password").isEqualTo(401);
        assertThat(login(NEW_PASSWORD)).as("new password").isEqualTo(200);
    }

    @Test
    void answersTheSameForAnUnknownAddress() throws Exception {
        assertThat(forgot("nobody@example.org")).isEqualTo(202);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void mailsNoUnconfirmedAccount() throws Exception {
        jdbc.sql("UPDATE app_user SET confirmed_at = NULL").update();
        assertThat(forgot(EMAIL)).isEqualTo(202);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void usesEachLinkOnce() throws Exception {
        forgot(EMAIL);
        String token = tokenFromMail();
        assertThat(reset(token, NEW_PASSWORD)).isEqualTo(204);
        assertThat(reset(token, "yet another password")).isEqualTo(410);
        assertThat(reset("made-up-token", NEW_PASSWORD)).isEqualTo(410);
        assertThat(login(NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void refusesAnExpiredLink() throws Exception {
        forgot(EMAIL);
        jdbc.sql("UPDATE app_user SET reset_expires_at = now() - interval '1 minute'").update();
        assertThat(reset(tokenFromMail(), NEW_PASSWORD)).isEqualTo(410);
        assertThat(login(OLD_PASSWORD)).isEqualTo(200);
    }

    @Test
    void refusesAShortPasswordAndKeepsTheLink() throws Exception {
        forgot(EMAIL);
        String token = tokenFromMail();
        assertThat(reset(token, "short")).isEqualTo(400);
        assertThat(reset(token, NEW_PASSWORD)).isEqualTo(204);
    }

    @Test
    void sendsNoSecondMailWithinTenMinutes() throws Exception {
        forgot(EMAIL);
        forgot(EMAIL);
        assertThat(mailer.sent).hasSize(1);
    }

    @Test
    void liftsTheLockLeftByWrongPasswords() throws Exception {
        for (int i = 0; i < LoginLimiter.MAX_FAILURES; i++) {
            login("a wrong password " + i);
        }
        assertThat(login(OLD_PASSWORD)).as("locked").isEqualTo(429);
        forgot(EMAIL);
        reset(tokenFromMail(), NEW_PASSWORD);
        assertThat(login(NEW_PASSWORD)).isEqualTo(200);
    }
}
