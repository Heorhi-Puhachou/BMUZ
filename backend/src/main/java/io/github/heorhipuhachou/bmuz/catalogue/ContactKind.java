package io.github.heorhipuhachou.bmuz.catalogue;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Від кантакту на старонцы выканаўцы. Пошта — адрас; астатняе — спасылка, і спасылка на сацыяльную сетку мусіць
 * весьці на сам сэрвіс, у тым ліку на яго паддамэн (www., m., імя.bandcamp.com).
 */
public enum ContactKind {
    EMAIL,
    WEBSITE,
    INSTAGRAM("instagram.com"),
    FACEBOOK("facebook.com"),
    YOUTUBE("youtube.com", "youtu.be"),
    BANDCAMP("bandcamp.com"),
    SOUNDCLOUD("soundcloud.com"),
    OTHER;

    private final List<String> hosts;

    ContactKind(String... hosts) {
        this.hosts = List.of(hosts);
    }

    public boolean accepts(String value) {
        if (this == EMAIL) {
            return value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
        }
        if (!value.matches("https?://\\S+")) {
            return false;
        }
        if (hosts.isEmpty()) {
            return true;
        }
        String host;
        try {
            host = URI.create(value).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return hosts.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d));
    }
}
