package io.github.heorhipuhachou.bmuz.catalogue;

import java.util.List;

/**
 * «Толькі бясплатнае»: альбом, у якога ёсьць спасылка FREE або NAME_YOUR_PRICE — на ўвесь альбом ці на
 * любы ягоны трэк. Умова напісаная адзін раз і ўстаўляецца ва ўсе запыты, дзе ёсьць альбом {@code a}.
 */
final class FreeFilter {

    private FreeFilter() {
    }

    static final List<PriceType> FREE_TYPES = List.of(PriceType.FREE, PriceType.NAME_YOUR_PRICE);

    /** JPQL; патрабуе пэрамэтраў {@code :freeOnly} і {@code :freeTypes}. */
    static final String ALBUM_A = """
            (:freeOnly = false OR EXISTS (
                SELECT l.id FROM Link l LEFT JOIN l.track t
                WHERE (l.album = a OR t.album = a) AND l.priceType IN :freeTypes))
            """;

    /** SQL для табліцы {@code album}, чый id даецца як {@code %s}; патрабуе пэрамэтра {@code :freeOnly}. */
    static String sql(String albumId) {
        return """
                (:freeOnly = false OR EXISTS (
                    SELECT 1 FROM link l LEFT JOIN track t ON t.id = l.track_id
                    WHERE (l.album_id = %1$s OR t.album_id = %1$s)
                      AND l.price_type IN ('FREE', 'NAME_YOUR_PRICE')))
                """.formatted(albumId);
    }
}
