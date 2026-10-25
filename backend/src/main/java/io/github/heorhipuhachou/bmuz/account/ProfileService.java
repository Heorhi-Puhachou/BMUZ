package io.github.heorhipuhachou.bmuz.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Імя і правапіс у профілі. Імя бачыць кожны пад камэнтарамі, таму яно не можа супадаць зь іншым — ні цалкам, ні
 * рознымі вялікімі й малымі літарамі. Правапіс уключаецца пры ўваходзе, і пераключальнік у загалоўку захоўвае яго.
 */
@Service
class ProfileService {

    static final int MAX_NAME = 40;

    /** Коды моў, як у браўзэры: тарашкевіца, афіцыйны правапіс, łacinka. */
    static final Set<String> SPELLINGS = Set.of("be-tarask", "be", "be-Latn");

    private final AppUserRepository users;

    ProfileService(AppUserRepository users) {
        this.users = users;
    }

    /** Пустое імя здымае імя наогул. */
    @Transactional
    public void rename(String email, String displayName) {
        AppUser user = user(email);
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        if (name != null) {
            if (name.codePointCount(0, name.length()) > MAX_NAME) {
                throw bad("a display name is at most " + MAX_NAME + " characters");
            }
            if (name.codePoints().anyMatch(Character::isISOControl)) {
                throw bad("a display name cannot contain control characters");
            }
            boolean taken = users.findByDisplayNameIgnoreCase(name).filter(u -> !u.getId().equals(user.getId()))
                    .isPresent();
            if (taken) {
                throw taken();
            }
        }
        user.rename(name);
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            // Іншы запіс узяў гэтае імя адначасова з намі; база не дазволіла.
            throw taken();
        }
    }

    @Transactional
    public void chooseSpelling(String email, String spelling) {
        if (spelling == null || !SPELLINGS.contains(spelling)) {
            throw bad("spelling is one of " + SPELLINGS);
        }
        user(email).chooseSpelling(spelling);
    }

    private AppUser user(String email) {
        return users.findByEmail(AppUser.normalise(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseStatusException taken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "this display name is taken");
    }
}
