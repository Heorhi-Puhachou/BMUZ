package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.catalogue.PriceType;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ByTxtParser {

    private static final Pattern ALBUM = Pattern.compile("^ {2}(\\d{4}) {2}(\\S.*)$");

    private static final Pattern LINK = Pattern.compile(
            "^ {8}(.+?)(?: \\(([^)]*)\\))?: (free|buy)(?: \\((.+)\\))? {2,}(https?://\\S+)$");

    private static final Pattern ALIASES = Pattern.compile("^([^\\[].*?) \\((.+)\\)$");

    private static final String NAME_YOUR_PRICE = "name your price";

    private record Album(int year, String title, List<LinkEntry> links) {
    }

    private record Artist(String name, List<String> aliases, List<Album> albums) {
    }

    public CatalogueFile parse(List<String> lines) {
        List<Artist> artists = new ArrayList<>();
        Artist artist = null;
        Album album = null;
        boolean inBody = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).stripTrailing();
            int lineNumber = i + 1;

            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith(" ")) {
                boolean startsArtist = i + 1 < lines.size() && ALBUM.matcher(lines.get(i + 1)).matches();
                if (!startsArtist) {
                    if (inBody) {
                        throw new ByTxtFormatException(lineNumber, "artist without albums: " + line);
                    }
                    continue;
                }
                inBody = true;
                artist = artist(line);
                artists.add(artist);
                album = null;
                continue;
            }
            if (!inBody) {
                continue;
            }

            Matcher albumMatch = ALBUM.matcher(line);
            if (albumMatch.matches()) {
                album = new Album(Integer.parseInt(albumMatch.group(1)), albumMatch.group(2), new ArrayList<>());
                artist.albums().add(album);
                continue;
            }

            Matcher linkMatch = LINK.matcher(line);
            if (linkMatch.matches() && album != null) {
                album.links().add(link(linkMatch));
                continue;
            }

            throw new ByTxtFormatException(lineNumber, "unrecognised line: " + line.strip());
        }

        List<ArtistEntry> entries = artists.stream()
                .map(a -> new ArtistEntry(a.name(), a.aliases(), a.albums().stream()
                        .map(al -> new AlbumEntry(al.year(), al.title(), al.links(), List.of()))
                        .toList()))
                .toList();
        return new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), entries);
    }

    private static Artist artist(String line) {
        Matcher aliases = ALIASES.matcher(line);
        if (aliases.matches()) {
            List<String> names = Arrays.stream(aliases.group(2).split(",")).map(String::strip).toList();
            return new Artist(aliases.group(1), names, new ArrayList<>());
        }
        return new Artist(line, List.of(), new ArrayList<>());
    }

    private static LinkEntry link(Matcher m) {
        String storeNote = m.group(2);
        String priceNote = m.group(4);
        PriceType priceType;
        if (m.group(3).equals("buy")) {
            priceType = PriceType.PAID;
        } else if (NAME_YOUR_PRICE.equals(priceNote)) {
            priceType = PriceType.NAME_YOUR_PRICE;
            priceNote = null;
        } else {
            priceType = PriceType.FREE;
        }
        return new LinkEntry(m.group(1), m.group(5), priceType, null, null, joinNotes(storeNote, priceNote), null);
    }

    private static String joinNotes(String first, String second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first + "; " + second;
    }
}
