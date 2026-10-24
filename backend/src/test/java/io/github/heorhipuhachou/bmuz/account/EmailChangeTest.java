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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Адрас мяняецца толькі з паролем і толькі пасьля спасылкі з ліста на новы адрас. */
@Import({TestcontainersConfiguration.class, RegistrationTest.Mails.class})
@SpringBootTest(properties = "bmuz.site-url=https://bmuz.example")
@AutoConfigureMockMvc
class EmailChangeTest {

    private static final String OLD = "old@example.org";
    private static final String NEW = "new@example.org";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailer mailer;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private MockHttpSession session;

    @BeforeEach
    void account(@Autowired LoginLimiter limiter) throws Exception {
        jdbc.sql("TRUNCATE app_user RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(OLD, encoder.encode(PASSWORD), Role.ADMIN));
        mailer.sent.clear();
        limiter.clear();
        session = login(OLD);
    }

    private int loginStatus(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private int change(String password, String email) throws Exception {
        return mvc.perform(post("/api/account/email").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + password + "\",\"email\":\"" + email + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int confirm(String token) throws Exception {
        return mvc.perform(post("/api/auth/email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int accountStatus(MockHttpSession s) throws Exception {
        return mvc.perform(get("/api/account/email").session(s)).andReturn().getResponse().getStatus();
    }

    private String token() {
        Matcher m = Pattern.compile("email\\?token=([A-Za-z0-9_-]+)").matcher(mailer.sent.getLast()[2]);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void changesOnlyAfterTheLinkAndThenEndsTheSessions() throws Exception {
        MockHttpSession other = login(OLD);
        assertThat(change(PASSWORD, " New@Example.org ")).isEqualTo(202);
        assertThat(mailer.sent.getLast()[0]).isEqualTo(NEW);
        assertThat(mailer.sent.getLast()[2]).contains("https://bmuz.example/email?token=");
        mvc.perform(get("/api/account/email").session(session))
                .andExpect(jsonPath("$.email").value(OLD))
                .andExpect(jsonPath("$.pending").value(NEW));
        assertThat(loginStatus(OLD)).as("old address before the link").isEqualTo(200);

        assertThat(confirm(token())).isEqualTo(204);
        assertThat(mailer.sent.getLast()[0]).as("notice to the old address").isEqualTo(OLD);
        assertThat(mailer.sent.getLast()[2]).contains(NEW);
        assertThat(accountStatus(session)).as("this session").isEqualTo(401);
        assertThat(accountStatus(other)).as("another session").isEqualTo(401);
        assertThat(loginStatus(OLD)).isEqualTo(401);
        mvc.perform(get("/api/account/email").session(login(NEW)))
                .andExpect(jsonPath("$.email").value(NEW))
                .andExpect(jsonPath("$.pending").isEmpty());
        assertThat(jdbc.sql("SELECT role FROM app_user").query(String.class).single()).isEqualTo("ADMIN");
    }

    @Test
    void needsThePassword() throws Exception {
        assertThat(change("a wrong password", NEW)).isEqualTo(403);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void needsALogin() throws Exception {
        assertThat(mvc.perform(post("/api/account/email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\",\"email\":\"" + NEW + "\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refusesWhatIsNotANewAddress() throws Exception {
        assertThat(change(PASSWORD, "not an address")).isEqualTo(400);
        assertThat(change(PASSWORD, "OLD@example.org")).isEqualTo(400);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void answersTheSameForATakenAddressAndChangesNothing() throws Exception {
        users.save(new AppUser(NEW, encoder.encode(PASSWORD), Role.ADMIN));
        assertThat(change(PASSWORD, NEW)).isEqualTo(202);
        assertThat(mailer.sent.getLast()[0]).isEqualTo(NEW);
        assertThat(mailer.sent.getLast()[2]).doesNotContain("token=");
        mvc.perform(get("/api/account/email").session(session)).andExpect(jsonPath("$.pending").isEmpty());
        assertThat(loginStatus(OLD)).isEqualTo(200);
    }

    @Test
    void refusesAnAddressTakenWhileTheLinkWaited() throws Exception {
        change(PASSWORD, NEW);
        String token = token();
        users.save(new AppUser(NEW, encoder.encode(PASSWORD), Role.ADMIN));
        assertThat(confirm(token)).isEqualTo(409);
        assertThat(loginStatus(OLD)).isEqualTo(200);
    }

    @Test
    void replacesAnUnconfirmedRegistrationOnTheNewAddress() throws Exception {
        users.save(AppUser.unconfirmed(NEW, encoder.encode("someone else's password"), "some-hash",
                Instant.now().plusSeconds(3600)));
        change(PASSWORD, NEW);
        assertThat(confirm(token())).isEqualTo(204);
        assertThat(loginStatus(NEW)).isEqualTo(200);
    }

    @Test
    void usesEachLinkOnceAndOnlyInTime() throws Exception {
        change(PASSWORD, NEW);
        String token = token();
        jdbc.sql("UPDATE app_user SET email_change_expires_at = now() - interval '1 minute'").update();
        assertThat(confirm(token)).as("expired").isEqualTo(410);

        jdbc.sql("UPDATE app_user SET email_change_expires_at = NULL").update();
        change(PASSWORD, NEW);
        token = token();
        assertThat(confirm(token)).isEqualTo(204);
        assertThat(confirm(token)).as("used").isEqualTo(410);
        assertThat(confirm("made-up-token")).isEqualTo(410);
    }

    @Test
    void sendsNoSecondMailWithinTenMinutes() throws Exception {
        change(PASSWORD, NEW);
        change(PASSWORD, NEW);
        assertThat(mailer.sent).hasSize(1);
        change(PASSWORD, "typo-fixed@example.org");
        assertThat(mailer.sent).as("another address").hasSize(2);
    }

    @Test
    void stopsAPasswordLinkSentToTheOldAddress() throws Exception {
        mvc.perform(post("/api/auth/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + OLD + "\"}"));
        Matcher m = Pattern.compile("reset\\?token=([A-Za-z0-9_-]+)").matcher(mailer.sent.getLast()[2]);
        assertThat(m.find()).isTrue();
        change(PASSWORD, NEW);
        confirm(token());
        assertThat(mvc.perform(post("/api/auth/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + m.group(1) + "\",\"password\":\"another long password\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(410);
    }

    /** Стары адрас вызвалены, і яго заняў іншы запіс: сэсія, што засталася ад старога адраса, не становіцца ягонай. */
    @Test
    void aLeftoverSessionDoesNotPassToTheNextOwnerOfTheAddress() throws Exception {
        MockHttpSession leftover = login(OLD);
        change(PASSWORD, NEW);
        confirm(token());
        users.save(new AppUser(OLD, encoder.encode("the next owner's password"), Role.ADMIN));
        assertThat(accountStatus(leftover)).isEqualTo(401);
    }
}
