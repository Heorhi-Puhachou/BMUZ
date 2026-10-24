package io.github.heorhipuhachou.bmuz.account;

/**
 * SUPER_ADMIN — адзіны на сайце: можа ўсё, што адміністратар, і яшчэ запрашае адміністратараў і выканаўцаў.
 * ARTIST — уліковы запіс, які рэдагуе сваіх выканаўцаў; якіх менавіта, кажа табліца artist_member.
 */
public enum Role {
    SUPER_ADMIN,
    ADMIN,
    ARTIST,
    USER;

    /** Ці рэдагуе ўвесь каталёг. */
    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
