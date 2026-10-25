package io.github.heorhipuhachou.bmuz.account;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthTest {

    private static final String EMAIL = "admin@example.org";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void oneAdmin(@Autowired LoginLimiter limiter) {
        limiter.clear();
        jdbc.sql("TRUNCATE app_user RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(EMAIL, encoder.encode(PASSWORD), Role.ADMIN));
    }

    private record Csrf(Cookie cookie, String token) {
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        return new Csrf(cookie, cookie.getValue());
    }

    private MvcResult login(String password, Csrf csrf) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    @Test
    void givesVisitorsNoCookies() throws Exception {
        for (String path : new String[]{"/api/artists", "/api/auth/me", "/api/search?q=x"}) {
            MvcResult result = mvc.perform(get(path)).andReturn();
            assertThat(result.getResponse().getHeaders("Set-Cookie")).as(path).isEmpty();
        }
    }

    @Test
    void keepsTheAdminApiClosedWithoutLogin() throws Exception {
        mvc.perform(get("/api/admin/edits")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesALoginWithoutTheCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logsTheAdminInAndOpensTheAdminApi() throws Exception {
        MvcResult result = login(PASSWORD, csrf());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/admin/edits").session(session)).andExpect(status().isOk());
    }

    @Test
    void refusesAWrongPasswordAndLocksAfterFiveTries() throws Exception {
        Csrf csrf = csrf();
        for (int i = 0; i < LoginLimiter.MAX_FAILURES; i++) {
            assertThat(login("wrong password!", csrf).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login(PASSWORD, csrf).getResponse().getStatus()).isEqualTo(429);
    }
}
