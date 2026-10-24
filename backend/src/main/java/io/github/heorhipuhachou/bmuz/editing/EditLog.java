package io.github.heorhipuhachou.bmuz.editing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

/** Журнал зьменаў каталёга. Зьмены бачныя на сайце адразу; адміністратар чытае гэты сьпіс, каб ведаць, што зьмянілі. */
@Component
public class EditLog {

    public record Entry(long id, Instant createdAt, String email, String action, String subject, Long artistId,
                        Long albumId, JsonNode before, JsonNode after) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;

    EditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void record(Editor editor, String action, String subject, Long artistId, Long albumId, Object before,
                Object after) {
        jdbc.sql("""
                        INSERT INTO edit_log (user_id, email, artist_id, album_id, action, subject, before, after)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)""")
                .params(editor.userId(), editor.email(), artistId, albumId, action, subject, write(before),
                        write(after))
                .update();
    }

    public List<Entry> latest(int page, int size) {
        return jdbc.sql("""
                        SELECT id, created_at, email, action, subject, artist_id, album_id, before::text, after::text
                        FROM edit_log ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?""")
                .params(size, page * size)
                .query((rs, n) -> new Entry(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getObject(6, Long.class), rs.getObject(7, Long.class),
                        read(rs.getString(8)), read(rs.getString(9))))
                .list();
    }

    private String write(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    private JsonNode read(String value) {
        return value == null ? null : json.readTree(value);
    }
}
