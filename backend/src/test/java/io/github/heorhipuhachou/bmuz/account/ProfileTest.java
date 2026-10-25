package io.github.heorhipuhachou.bmuz.account;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Імя і правапіс у профілі: імя адно на ўвесь сайт, правапіс вяртаецца пры ўваходзе. Токен CSRF бярэцца як у
 * браўзэры, з /api/auth/csrf, а не праз csrf() з spring-security-test: той назаўжды падмяняе сховішча токенаў у
 * агульным кантэксьце, і AuthTest, які правярае сапраўдны кукі, пасьля яго падае.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProfileTest {

    private static final String ME = "me@example.org";
    private static final String OTHER = "other@example.org";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    private MockHttpSession session;

    @BeforeEach
    void accounts(@Autowired JdbcClient jdbc, @Autowired AppUserRepository users, @Autowired PasswordEncoder encoder,
                  @Autowired LoginLimiter limiter) throws Exception {
        jdbc.sql("TRUNCATE app_user RESTART IDENTITY CASCADE").update();
        users.save(new AppUser(ME, encoder.encode(PASSWORD), Role.ADMIN));
        users.save(new AppUser(OTHER, encoder.encode(PASSWORD), Role.SUPER_ADMIN));
        limiter.clear();
        session = login(ME);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie cookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
    }

    private MvcResult loginResult(String email) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) loginResult(email).getRequest().getSession(false);
    }

    private int send(String path, String json, MockHttpSession session) throws Exception {
        var request = withCsrf(put(path)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void startsEmpty() throws Exception {
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.displayName").value(nullValue()))
                .andExpect(jsonPath("$.spelling").value(nullValue()));
    }

    @Test
    void theSpellingComesBackAtLogin() throws Exception {
        assertThat(send("/api/account/spelling", "{\"spelling\":\"be-Latn\"}", session))
                .isEqualTo(204);
        assertThat(loginResult(ME).getResponse().getContentAsString()).contains("\"spelling\":\"be-Latn\"");
        assertThat(send("/api/account/spelling", "{\"spelling\":\"en\"}", session))
                .isEqualTo(400);
    }

    @Test
    void aDisplayNameIsStrippedAndCanBeRemoved() throws Exception {
        assertThat(send("/api/account/display-name", "{\"displayName\":\"  Лявон  \"}", session)).isEqualTo(204);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.displayName").value("Лявон"));
        assertThat(send("/api/account/display-name", "{\"displayName\":\" \"}", session)).isEqualTo(204);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.displayName").value(nullValue()));
    }

    @Test
    void aDisplayNameBelongsToOneAccount() throws Exception {
        send("/api/account/display-name", "{\"displayName\":\"Лявон\"}", session);
        MockHttpSession other = login(OTHER);
        assertThat(send("/api/account/display-name", "{\"displayName\":\"ЛЯВОН\"}", other)).isEqualTo(409);
        assertThat(send("/api/account/display-name", "{\"displayName\":\"лявон\"}", session)).as("own name, new case")
                .isEqualTo(204);
    }

    @Test
    void refusesALongOrBrokenName() throws Exception {
        assertThat(send("/api/account/display-name", "{\"displayName\":\"" + "я".repeat(41) + "\"}", session))
                .isEqualTo(400);
        assertThat(send("/api/account/display-name", "{\"displayName\":\"" + "я".repeat(40) + "\"}", session))
                .isEqualTo(204);
        assertThat(send("/api/account/display-name", "{\"displayName\":\"a\\nb\"}", session)).isEqualTo(400);
    }

    @Test
    void needsALogin() throws Exception {
        assertThat(send("/api/account/spelling", "{\"spelling\":\"be\"}", null))
                .isEqualTo(401);
    }
}
