package io.github.heorhipuhachou.bmuz.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Console;

/**
 * Стварае адміністратара або мяняе ягоны пароль: {@code --bmuz.admin.email=АДРАС}. Пароль бярэцца з пераменнай
 * асяродзьдзя BMUZ_ADMIN_PASSWORD або пытаецца ў тэрмінале без рэха, каб не трапіць у гісторыю каманд ці журнал.
 * З {@code --bmuz.admin.super=true} гэты адрас становіцца суперадміністратарам, а ранейшы — звычайным адміністратарам:
 * суперадміністратар заўсёды адзін.
 */
@Component
class AdminAccountRunner implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(AdminAccountRunner.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    private final Environment env;

    AdminAccountRunner(AppUserRepository users, PasswordEncoder encoder, TransactionTemplate transaction,
                       Environment env) {
        this.users = users;
        this.encoder = encoder;
        this.transaction = transaction;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = env.getProperty("bmuz.admin.email");
        if (email == null) {
            return;
        }
        boolean makeSuper = env.getProperty("bmuz.admin.super", Boolean.class, false);
        String password = password();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("the password needs at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        String hash = encoder.encode(password);
        String normalised = AppUser.normalise(email);
        boolean created = Boolean.TRUE.equals(transaction.execute(status -> {
            AppUser user = users.findByEmail(normalised).orElse(null);
            if (user != null && !user.getRole().isAdmin()) {
                throw new IllegalStateException(normalised + " exists and is not an admin");
            }
            if (makeSuper && (user == null || user.getRole() != Role.SUPER_ADMIN)) {
                // Спачатку ранейшы суперадміністратар саступае: у базе іх не можа быць двух нават на момант.
                users.findByRole(Role.SUPER_ADMIN).ifPresent(previous -> {
                    previous.setRole(Role.ADMIN);
                    log.info("{} is no longer the super-admin", previous.getEmail());
                });
                users.flush();
            }
            if (user == null) {
                users.save(new AppUser(normalised, hash, makeSuper ? Role.SUPER_ADMIN : Role.ADMIN));
                return true;
            }
            user.changePassword(hash);
            if (makeSuper) {
                user.setRole(Role.SUPER_ADMIN);
            }
            return false;
        }));
        log.info(created ? "Admin {} created" : "Password of admin {} changed", normalised);
        if (makeSuper) {
            log.info("{} is the super-admin", normalised);
        }
    }

    private String password() {
        String fromEnv = System.getenv("BMUZ_ADMIN_PASSWORD");
        if (fromEnv != null) {
            return fromEnv;
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("set BMUZ_ADMIN_PASSWORD or run in a terminal");
        }
        char[] first = console.readPassword("Password: ");
        char[] second = console.readPassword("Again: ");
        if (first == null || !new String(first).equals(new String(second))) {
            throw new IllegalArgumentException("the passwords differ");
        }
        return new String(first);
    }
}
