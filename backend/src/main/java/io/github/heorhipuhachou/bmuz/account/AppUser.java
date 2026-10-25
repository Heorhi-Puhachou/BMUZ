package io.github.heorhipuhachou.bmuz.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.Locale;

@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Instant confirmedAt;

    private String confirmTokenHash;

    private Instant confirmExpiresAt;

    private String resetTokenHash;

    private Instant resetExpiresAt;

    private String newEmail;

    private String emailChangeTokenHash;

    private Instant emailChangeExpiresAt;

    private String displayName;

    private String spelling;

    protected AppUser() {
    }

    /** Адміністратар, створаны камандай на сэрвэры, адразу пацьверджаны. */
    public AppUser(String email, String passwordHash, Role role) {
        this.email = normalise(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.confirmedAt = role.isAdmin() ? Instant.now() : null;
    }

    /** Выканаўца, які прыняў запрашэньне: спасылка з ліста ўжо пацьвердзіла адрас. */
    static AppUser artist(String email, String passwordHash) {
        return confirmed(email, passwordHash, Role.ARTIST);
    }

    private static AppUser confirmed(String email, String passwordHash, Role role) {
        AppUser user = new AppUser(email, passwordHash, role);
        user.confirmedAt = Instant.now();
        return user;
    }

    /** Звычайны карыстальнік, якога запрасілі як выканаўцу. Адміністратар застаецца адміністратарам. */
    void becomeArtist() {
        if (role == Role.USER) {
            role = Role.ARTIST;
        }
    }

    /** Адміністратарам, па запрашэньні суперадміністратара. Суперадміністратар застаецца суперадміністратарам. */
    void becomeAdmin() {
        if (role == Role.USER || role == Role.ARTIST) {
            role = Role.ADMIN;
        }
    }

    /** Мяняе ролю без правілаў вышэй — толькі для каманды на сэрвэры, што перадае суперадміністратара. */
    void setRole(Role role) {
        this.role = role;
    }

    /** Выканаўца, у якога забралі апошняга выканаўцу, становіцца звычайным карыстальнікам. */
    void becomeUser() {
        if (role == Role.ARTIST) {
            role = Role.USER;
        }
    }

    static AppUser unconfirmed(String email, String passwordHash, String tokenHash, Instant expiresAt) {
        AppUser user = new AppUser(email, passwordHash, Role.USER);
        user.confirmTokenHash = tokenHash;
        user.confirmExpiresAt = expiresAt;
        return user;
    }

    boolean confirm(Instant now) {
        if (confirmedAt != null || confirmExpiresAt == null || now.isAfter(confirmExpiresAt)) {
            return false;
        }
        confirmedAt = now;
        confirmTokenHash = null;
        confirmExpiresAt = null;
        return true;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    boolean confirmExpiresAfter(Instant moment) {
        return confirmExpiresAt != null && confirmExpiresAt.isAfter(moment);
    }

    void requestReset(String tokenHash, Instant expiresAt) {
        resetTokenHash = tokenHash;
        resetExpiresAt = expiresAt;
    }

    boolean resetExpiresAfter(Instant moment) {
        return resetExpiresAt != null && resetExpiresAt.isAfter(moment);
    }

    /** Мяняе пароль па спасылцы з ліста. Спасылка працуе адзін раз і толькі да свайго тэрміну. */
    boolean reset(String passwordHash, Instant now) {
        if (resetExpiresAt == null || now.isAfter(resetExpiresAt)) {
            return false;
        }
        this.passwordHash = passwordHash;
        resetTokenHash = null;
        resetExpiresAt = null;
        return true;
    }

    /** tokenHash — null, калі новы адрас заняты і спасылкі няма: тады запіс памятае толькі паўзу паміж лістамі. */
    void requestEmailChange(String newEmail, String tokenHash, Instant expiresAt) {
        this.newEmail = newEmail;
        emailChangeTokenHash = tokenHash;
        emailChangeExpiresAt = expiresAt;
    }

    /** Ці прасілі ўжо зьмены на гэты самы адрас пазьней за moment — тады новага ліста не трэба. */
    boolean emailChangeRequestedAfter(String newEmail, Instant moment) {
        return newEmail.equals(this.newEmail) && emailChangeExpiresAt != null && emailChangeExpiresAt.isAfter(moment);
    }

    /** Новы адрас, які чакае пацьверджаньня, калі спасылка яшчэ працуе. */
    String pendingEmail(Instant now) {
        return emailChangeTokenHash != null && emailChangeExpiresAt != null && !now.isAfter(emailChangeExpiresAt)
                ? newEmail : null;
    }

    /**
     * Мяняе адрас па спасылцы з ліста на новы адрас. Спасылка на новы пароль, дасланая на стары адрас, перастае
     * працаваць: стары адрас ужо не кіруе запісам.
     */
    void changeEmail() {
        email = newEmail;
        newEmail = null;
        emailChangeTokenHash = null;
        emailChangeExpiresAt = null;
        resetTokenHash = null;
        resetExpiresAt = null;
    }

    public static String normalise(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Правілы імя правярае {@link ProfileService}; null — імя няма. */
    void rename(String displayName) {
        this.displayName = displayName;
    }

    void chooseSpelling(String spelling) {
        this.spelling = spelling;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSpelling() {
        return spelling;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }
}
