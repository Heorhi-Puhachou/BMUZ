package io.github.heorhipuhachou.bmuz.account;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * З аднаго IP-адрасу — не болей за {@value #MAX} рэгістрацый за {@link #WINDOW}. Адрасы трымаюцца толькі ў памяці і
 * забываюцца, як толькі выходзяць з акна; пасьля перазапуску сэрвэра лік пачынаецца нанова.
 */
@Component
class RegistrationLimiter {

    static final int MAX = 7;
    static final Duration WINDOW = Duration.ofHours(24);

    private final Map<String, List<Instant>> attempts = new HashMap<>();
    private final Clock clock;

    RegistrationLimiter() {
        this(Clock.systemUTC());
    }

    RegistrationLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Лічыць спробу і кажа, ці яна ў межах. */
    synchronized boolean allow(String address) {
        Instant now = clock.instant();
        Instant since = now.minus(WINDOW);
        attempts.values().forEach(times -> times.removeIf(t -> !t.isAfter(since)));
        attempts.values().removeIf(List::isEmpty);
        List<Instant> times = attempts.computeIfAbsent(address, a -> new ArrayList<>());
        if (times.size() >= MAX) {
            return false;
        }
        times.add(now);
        return true;
    }

    /** Для тэстаў. */
    synchronized void clear() {
        attempts.clear();
    }
}
