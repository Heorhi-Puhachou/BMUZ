package io.github.heorhipuhachou.bmuz.cover;

import java.io.IOException;

/**
 * Дзе ляжаць файлы вокладак. Ключ заўсёды пачынаецца з {@code covers/}, і файл даступны па адрасе {@code /ключ}:
 * на сэрвэры CloudFront аддае {@code /covers/*} з S3, на ноўтбуку — сам бэкэнд з лякальнай тэчкі.
 */
public interface CoverStorage {

    void put(String key, byte[] jpeg) throws IOException;

    void delete(String key) throws IOException;

    static String url(String key) {
        return "/" + key;
    }
}
