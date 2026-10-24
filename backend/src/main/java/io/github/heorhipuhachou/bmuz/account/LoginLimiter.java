package io.github.heorhipuhachou.bmuz.account;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Пасьля {@value #MAX_FAILURES} няўдалых спробаў уваход на гэты адрас зачыняецца на {@link #LOCK}. */
@Component
class LoginLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(15);

    private record Failures(int count, Instant lockedUntil) {
    }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginLimiter() {
        this(Clock.systemUTC());
    }

    LoginLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean isLocked(String email) {
        Failures f = failures.get(email);
        return f != null && f.lockedUntil() != null && clock.instant().isBefore(f.lockedUntil());
    }

    void failed(String email) {
        failures.compute(email, (key, f) -> {
            int count = (f == null || f.lockedUntil() != null && !clock.instant().isBefore(f.lockedUntil()))
                    ? 1 : f.count() + 1;
            return new Failures(count, count >= MAX_FAILURES ? clock.instant().plus(LOCK) : null);
        });
    }

    void succeeded(String email) {
        failures.remove(email);
    }

    /** Для тэстаў: усе блякаваньні здымаюцца. */
    void clear() {
        failures.clear();
    }
}
