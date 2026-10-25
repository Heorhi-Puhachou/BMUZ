package io.github.heorhipuhachou.bmuz.community;

import io.github.heorhipuhachou.bmuz.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Пакуль bmuz.reviews.enabled выключаны — а ён выключаны па змоўчаньні, — водгукаў і ацэнак на сайце няма. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReviewsOffTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void nothingShowsWhileSwitchedOff(@Autowired JdbcClient jdbc) throws Exception {
        jdbc.sql("TRUNCATE artist, album RESTART IDENTITY CASCADE").update();
        long album = jdbc.sql("INSERT INTO album (year, title) VALUES (2020, 'A') RETURNING id").query(Long.class)
                .single();
        mvc.perform(get("/api/albums/" + album + "/reviews")).andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.reviews").value(false));
    }
}
