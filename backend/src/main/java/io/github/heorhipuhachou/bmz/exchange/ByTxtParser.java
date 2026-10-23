package io.github.heorhipuhachou.bmz.exchange;

import io.github.heorhipuhachou.bmz.catalogue.PriceType;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.AlbumEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.ArtistEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.CreditEntry;
import io.github.heorhipuhachou.bmz.exchange.CatalogueFile.LinkEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ByTxtParser {

    private static final Pattern ALBUM = Pattern.compile("^ {2}(\\d{4}) {2}(\\S.*)$");

    private static final Pattern LINK = Pattern.compile(
            "^ {8}(.+?)(?: \\(([^)]*)\\))?: (free|buy)(?: \\((.+)\\))? {2,}(https?://\\S+)$");

    private static final Pattern ALIASES = Pattern.compile("^([^\\[].*?) \\((.+)\\)$");

    /** Радок «J:Mors & Lavon Volski» — некалькі выканаўцаў; паміж імёнамі стаіць « & » або «, ». */
    private static final Pattern CREDIT_SEPARATOR = Pattern.compile(" & |, ");

    private static final String NAME_YOUR_PRICE = "name your price";

    private record PendingAlbum(int year, String title, List<CreditEntry> credit, List<LinkEntry> links) {
    }

    public CatalogueFile parse(List<String> lines) {
        Map<String, ArtistEntry> artists = new LinkedHashMap<>();
        List<PendingAlbum> albums = new ArrayList<>();
        List<CreditEntry> credit = null;
        PendingAlbum album = null;
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
                credit = credit(line, artists);
                album = null;
                continue;
            }
            if (!inBody) {
                continue;
            }

            Matcher albumMatch = ALBUM.matcher(line);
            if (albumMatch.matches()) {
                album = new PendingAlbum(Integer.parseInt(albumMatch.group(1)), albumMatch.group(2), credit,
                        new ArrayList<>());
                albums.add(album);
                continue;
            }

            Matcher linkMatch = LINK.matcher(line);
            if (linkMatch.matches() && album != null) {
                album.links().add(link(linkMatch));
                continue;
            }

            throw new ByTxtFormatException(lineNumber, "unrecognised line: " + line.strip());
        }

        List<AlbumEntry> entries = albums.stream()
                .map(a -> new AlbumEntry(a.year(), a.title(), a.credit(), a.links(), List.of()))
                .toList();
        return new CatalogueFile(CatalogueFile.FORMAT_VERSION, List.of(), List.copyOf(artists.values()), entries);
    }

    private static List<CreditEntry> credit(String line, Map<String, ArtistEntry> artists) {
        Matcher aliases = ALIASES.matcher(line);
        if (aliases.matches()) {
            String name = aliases.group(1);
            List<String> names = Arrays.stream(aliases.group(2).split(",")).map(String::strip).toList();
            artists.putIfAbsent(name, new ArtistEntry(name, names));
            return List.of(new CreditEntry(name, null));
        }

        List<CreditEntry> credit = new ArrayList<>();
        Matcher separator = CREDIT_SEPARATOR.matcher(line);
        int start = 0;
        while (separator.find()) {
            credit.add(new CreditEntry(line.substring(start, separator.start()), separator.group()));
            start = separator.end();
        }
        credit.add(new CreditEntry(line.substring(start), null));
        for (CreditEntry entry : credit) {
            artists.putIfAbsent(entry.artist(), new ArtistEntry(entry.artist(), List.of()));
        }
        return List.copyOf(credit);
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
