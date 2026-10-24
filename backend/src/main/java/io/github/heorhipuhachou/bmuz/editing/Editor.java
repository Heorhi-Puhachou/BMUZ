package io.github.heorhipuhachou.bmuz.editing;

import java.util.Collection;
import java.util.Set;

/** Хто праўляе: адміністратар можа ўсё, выканаўца — толькі сваіх выканаўцаў і іхныя альбомы. */
record Editor(long userId, String email, boolean admin, Set<Long> artists) {

    boolean manages(long artistId) {
        return admin || artists.contains(artistId);
    }

    boolean managesAny(Collection<Long> artistIds) {
        return admin || artistIds.stream().anyMatch(artists::contains);
    }
}
