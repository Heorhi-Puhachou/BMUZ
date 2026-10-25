package io.github.heorhipuhachou.bmuz.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication
class AuthController {

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    /**
     * artists — выканаўцы, якіх гэты запіс праўляе; адміністратар праўляе ўсіх і без гэтага сьпісу. displayName і
     * spelling — з профілю, null, пакуль не зададзеныя. reviewsFrom — з якога моманту можна пісаць водгукі.
     */
    record Me(String email, String role, List<Long> artists, String displayName, String spelling,
              Instant reviewsFrom) {
    }

    /** reviews — ці ёсьць на сайце водгукі і ацэнкі (bmuz.reviews.enabled). */
    record Config(boolean registration, boolean reviews) {
    }

    record RegisterRequest(String email, String password) {
    }

    record ConfirmRequest(String token) {
    }

    record ForgotRequest(String email) {
    }

    record ResetRequest(String token, String password) {
    }

    record AcceptRequest(String token, String password) {
    }


    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final LoginLimiter limiter;
    private final RegistrationService registration;
    private final PasswordResetService passwordReset;
    private final TeamService team;
    private final EmailChangeService emailChange;
    private final AppUserRepository users;
    private final boolean reviews;

    AuthController(AuthenticationManager authentication, SecurityContextRepository contexts, LoginLimiter limiter,
                   RegistrationService registration, PasswordResetService passwordReset,
                   TeamService team, EmailChangeService emailChange, AppUserRepository users,
                   @Value("${bmuz.reviews.enabled:false}") boolean reviews) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.limiter = limiter;
        this.registration = registration;
        this.passwordReset = passwordReset;
        this.team = team;
        this.emailChange = emailChange;
        this.users = users;
        this.reviews = reviews;
    }

    @GetMapping("/config")
    Config config() {
        return new Config(registration.isEnabled(), reviews);
    }

    /** Адказ заўсёды 202, нават калі адрас ужо ёсьць: форма не павінна выдаваць, хто зарэгістраваны. */
    @PostMapping("/register")
    ResponseEntity<Void> register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        registration.register(body.email() == null ? "" : body.email(), body.password(), clientAddress(request));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm")
    ResponseEntity<Void> confirm(@RequestBody ConfirmRequest body) {
        return registration.confirm(body.token())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.GONE).build();
    }

    /** Адказ заўсёды 202, ці ёсьць такі адрас, ці не. */
    @PostMapping("/forgot")
    ResponseEntity<Void> forgot(@RequestBody ForgotRequest body) {
        passwordReset.request(body.email() == null ? "" : body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset(@RequestBody ResetRequest body) {
        return passwordReset.reset(body.token(), body.password())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.GONE).build();
    }

    /** Каго і куды запрашае спасылка — старонка паказвае гэта перад тым, як прыняць. */
    @GetMapping("/invite")
    ResponseEntity<TeamService.InviteInfo> invite(@RequestParam String token) {
        return team.inviteInfo(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.GONE).build());
    }

    @PostMapping("/invite")
    ResponseEntity<Void> acceptInvite(@RequestBody AcceptRequest body, Authentication auth) {
        String current = auth == null || !auth.isAuthenticated() ? null : auth.getName();
        return team.accept(body.token(), body.password(), current)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.GONE).build();
    }

    /** Спасылка з ліста на новы адрас. Уваходзіць для гэтага не трэба: ліст могуць адкрыць на іншым прыладзе. */
    @PostMapping("/email")
    ResponseEntity<Void> confirmEmail(@RequestBody ConfirmRequest body) {
        return emailChange.confirm(body.token())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.GONE).build();
    }

    /** Старонка ўваходу бярэ тут токен CSRF; толькі пасьля гэтага браўзэр атрымлівае ягоны кукі. */
    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/login")
    ResponseEntity<Me> login(@RequestBody LoginRequest login, HttpServletRequest request,
                             HttpServletResponse response) {
        String email = AppUser.normalise(login.email());
        if (limiter.isLocked(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        Authentication result;
        try {
            result = authentication.authenticate(new UsernamePasswordAuthenticationToken(email, login.password()));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (AuthenticationException e) {
            limiter.failed(email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        limiter.succeeded(email);

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.ok(describe(result));
    }

    @GetMapping("/me")
    ResponseEntity<Me> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(describe(auth));
    }

    private Me describe(Authentication auth) {
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .findFirst().orElse(Role.USER.name());
        AppUser user = users.findByEmail(AppUser.normalise(auth.getName())).orElse(null);
        return new Me(auth.getName(), role, team.managedArtists(auth.getName()).stream().sorted().toList(),
                user == null ? null : user.getDisplayName(), user == null ? null : user.getSpelling(),
                user == null ? null : user.reviewsFrom());
    }

    /**
     * IP-адрас наведніка. Сэрвэр адказвае толькі CloudFront, а той дапісвае адрас наведніка ў канец X-Forwarded-For;
     * усё, што перад ім, мог напісаць сам кліент, таму бярэцца апошні.
     */
    static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] addresses = forwarded.split(",");
        return addresses[addresses.length - 1].strip();
    }
}
