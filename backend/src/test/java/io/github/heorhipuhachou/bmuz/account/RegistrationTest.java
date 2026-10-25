package io.github.heorhipuhachou.bmuz.account;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, RegistrationTest.Mails.class})
@SpringBootTest(properties = {"bmuz.registration.enabled=true", "bmuz.site-url=https://bmuz.example"})
@AutoConfigureMockMvc
class RegistrationTest {

    private static final String EMAIL = "Listener@Example.org";
    private static final String PASSWORD = "a long enough password";

    @TestConfiguration
    static class Mails {
        @Bean
        @Primary
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }

    static class RecordingMailer implements Mailer {
        final List<String[]> sent = new ArrayList<>();

        @Override
        public void send(String to, String subject, String text) {
            sent.add(new String[]{to, subject, text});
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailer mailer;

    @BeforeEach
    void clean(@Autowired JdbcClient jdbc, @Autowired LoginLimiter limiter,
               @Autowired RegistrationLimiter registrations, @Autowired RegistrationAlarm alarm) {
        jdbc.sql("TRUNCATE app_user RESTART IDENTITY CASCADE").update();
        mailer.sent.clear();
        limiter.clear();
        registrations.clear();
        alarm.clear();
    }

    private int register(String email, String password) throws Exception {
        return register(email, password, "192.0.2.1");
    }

    /** forwardedFor — загаловак X-Forwarded-For, як яго прысылае CloudFront. */
    private int register(String email, String password, String forwardedFor) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", forwardedFor)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
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

    private int confirm(String token) throws Exception {
        return mvc.perform(post("/api/auth/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void registersConfirmsAndLogsIn() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.registration").value(true));
        assertThat(register(EMAIL, PASSWORD)).isEqualTo(202);
        assertThat(mailer.sent).hasSize(1);
        assertThat(mailer.sent.getFirst()[0]).isEqualTo("listener@example.org");
        assertThat(mailer.sent.getFirst()[2]).contains("https://bmuz.example/confirm?token=");

        assertThat(login(PASSWORD)).as("before confirming").isEqualTo(403);
        assertThat(confirm(tokenFromMail())).isEqualTo(204);
        assertThat(login(PASSWORD)).as("after confirming").isEqualTo(200);
    }

    @Test
    void doesNotRevealAnUnconfirmedAccountToAWrongPassword() throws Exception {
        register(EMAIL, PASSWORD);
        assertThat(login("some other password")).isEqualTo(401);
    }

    @Test
    void answersTheSameForAnAddressThatIsAlreadyRegistered() throws Exception {
        register(EMAIL, PASSWORD);
        confirm(tokenFromMail());
        mailer.sent.clear();
        assertThat(register(EMAIL, "another long password")).isEqualTo(202);
        assertThat(mailer.sent).isEmpty();
        assertThat(login(PASSWORD)).isEqualTo(200);
    }

    @Test
    void sendsNoSecondMailWithinTenMinutes() throws Exception {
        register(EMAIL, PASSWORD);
        register(EMAIL, PASSWORD);
        assertThat(mailer.sent).hasSize(1);
    }

    @Test
    void usesEachLinkOnce() throws Exception {
        register(EMAIL, PASSWORD);
        String token = tokenFromMail();
        assertThat(confirm(token)).isEqualTo(204);
        assertThat(confirm(token)).isEqualTo(410);
        assertThat(confirm("made-up-token")).isEqualTo(410);
    }

    @Test
    void refusesAShortPassword() throws Exception {
        assertThat(register(EMAIL, "short")).isEqualTo(400);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void givesAUserNoAdminRights() throws Exception {
        register(EMAIL, PASSWORD);
        confirm(tokenFromMail());
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/admin/artists/1/permissions").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void sevenRegistrationsFromOneAddressADay() throws Exception {
        for (int i = 1; i <= 7; i++) {
            assertThat(register("user" + i + "@example.org", PASSWORD, "203.0.113.5")).isEqualTo(202);
        }
        assertThat(register("user8@example.org", PASSWORD, "203.0.113.5")).isEqualTo(429);
        assertThat(register("user8@example.org", PASSWORD, "203.0.113.5, 198.51.100.7"))
                .as("CloudFront's address, the last one, counts").isEqualTo(202);
        assertThat(register("user9@example.org", PASSWORD, "198.51.100.9, 203.0.113.5"))
                .as("what the client wrote in front does not").isEqualTo(429);
    }

    @Test
    void mailsTheAdminsWhenMoreThanTenRegisterInAnHour(@Autowired JdbcClient jdbc,
                                                       @Autowired AppUserRepository users) throws Exception {
        users.save(new AppUser("admin@example.org", "x", Role.ADMIN));
        jdbc.sql("UPDATE app_user SET created_at = now() - interval '2 days'").update();
        jdbc.sql("""
                INSERT INTO app_user (email, password_hash, role)
                SELECT 'bot' || g || '@example.org', 'x', 'USER' FROM generate_series(1, 9) g""").update();

        register("user10@example.org", PASSWORD);
        assertThat(alarms()).as("ten in the hour").isZero();
        register("user11@example.org", PASSWORD);
        assertThat(alarms()).as("eleven").isEqualTo(1);
        register("user12@example.org", PASSWORD);
        assertThat(alarms()).as("at most one an hour").isEqualTo(1);
    }

    private long alarms() {
        return mailer.sent.stream().filter(m -> m[0].equals("admin@example.org")).count();
    }
}
