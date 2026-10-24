package io.github.heorhipuhachou.bmuz.account;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Той, хто ўвайшоў. Апроч адраса памятае нумар запісу: адрас можна зьмяніць, і пасьля гэтага стары адрас можа
 * зарэгістраваць хтосьці іншы. Сэсія, што засталася ад старога адраса, не павінна стаць сэсіяй новага ўладальніка.
 */
class Account extends User {

    private final long id;

    Account(long id, String email, String passwordHash, boolean enabled,
            Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, enabled, true, true, true, authorities);
        this.id = id;
    }

    long getId() {
        return id;
    }

}
