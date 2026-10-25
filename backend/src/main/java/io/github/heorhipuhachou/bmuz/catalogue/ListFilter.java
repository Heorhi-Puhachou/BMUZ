package io.github.heorhipuhachou.bmuz.catalogue;

import java.util.List;

/**
 * Фільтры сьпісаў. freeOnly — «толькі бясплатнае»: альбом, у якога ёсьць спасылка FREE або NAME_YOUR_PRICE — на ўвесь
 * альбом ці на любы ягоны трэк. uncheckedBy — нумар адміністратара, які хоча бачыць толькі альбомы, што ён не
 * праглядзеў або што зьмянілі пасьля ягонага прагляду; 0 — без гэтага фільтру. Умовы напісаныя адзін раз і
 * ўстаўляюцца ва ўсе запыты, дзе ёсьць альбом {@code a}.
 */
public record ListFilter(boolean freeOnly, long uncheckedBy) {

    public static final ListFilter NONE = new ListFilter(false, 0);

    static final List<PriceType> FREE_TYPES = List.of(PriceType.FREE, PriceType.NAME_YOUR_PRICE);

    /** JPQL; патрабуе пэрамэтраў {@code :freeOnly}, {@code :freeTypes} і {@code :uncheckedBy}. */
    static final String ALBUM_A = """
            (:freeOnly = false OR EXISTS (
                SELECT l.id FROM Link l LEFT JOIN l.track t
                WHERE (l.album = a OR t.album = a) AND l.priceType IN :freeTypes))
            AND (:uncheckedBy = 0 OR NOT EXISTS (
                SELECT k.id FROM AlbumCheck k
                WHERE k.id.albumId = a.id AND k.id.userId = :uncheckedBy AND k.checkedAt >= a.updatedAt))
            """;

    /** SQL для табліцы {@code album}, чый id даецца як {@code %s}; патрабуе пэрамэтраў {@code :freeOnly} і {@code :uncheckedBy}. */
    static String sql(String albumId) {
        return """
                (:freeOnly = false OR EXISTS (
                    SELECT 1 FROM link l LEFT JOIN track t ON t.id = l.track_id
                    WHERE (l.album_id = %1$s OR t.album_id = %1$s)
                      AND l.price_type IN ('FREE', 'NAME_YOUR_PRICE')))
                AND (:uncheckedBy = 0 OR NOT EXISTS (
                    SELECT 1 FROM album_check k JOIN album x ON x.id = k.album_id
                    WHERE k.album_id = %1$s AND k.user_id = :uncheckedBy AND k.checked_at >= x.updated_at))
                """.formatted(albumId);
    }
}
