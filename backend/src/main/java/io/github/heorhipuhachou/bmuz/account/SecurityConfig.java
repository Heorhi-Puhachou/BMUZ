package io.github.heorhipuhachou.bmuz.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.List;

/**
 * Уваход толькі для адміністратара. Наведнікі не атрымліваюць ніякіх кукі: сэсія ствараецца толькі пры ўваходзе,
 * а кукі абароны ад падробленых запытаў (CSRF) — толькі калі старонка ўваходу просіць яе праз /api/auth/csrf.
 */
@Configuration
class SecurityConfig {

    /**
     * Правілы для вэб-запытаў — толькі калі праграма працуе як сайт. Пры разавых камандах без вэба
     * (імпарт каталёга, стварэньне адміністратара) іх няма, а хэшаваньне пароляў ёсьць заўсёды.
     */
    @Configuration
    @ConditionalOnWebApplication
    static class Web {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users) throws Exception {
            CookieCsrfTokenRepository csrfCookies = CookieCsrfTokenRepository.withHttpOnlyFalse();
            csrfCookies.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict"));

            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/super/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers("/api/admin/**").hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            // Каго з выканаўцаў можна рэдагаваць, правярае сам рэдактар: тут — толькі «увайшоў».
                            .requestMatchers("/api/edit/**").authenticated()
                            .requestMatchers("/api/account/**").authenticated()
                            .anyRequest().permitAll())
                    .csrf(csrf -> csrf
                            .csrfTokenRepository(csrfCookies)
                            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                    .addFilterAfter(new FreshRolesFilter(users, securityContextRepository()),
                            SecurityContextHolderFilter.class)
                    .requestCache(cache -> cache.disable())
                    .formLogin(form -> form.disable())
                    .httpBasic(basic -> basic.disable())
                    .exceptionHandling(e -> e.authenticationEntryPoint(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .logout(logout -> logout
                            .logoutUrl("/api/auth/logout")
                            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));
            return http.build();
        }

        @Bean
        SecurityContextRepository securityContextRepository() {
            return new HttpSessionSecurityContextRepository();
        }
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return email -> users.findByEmail(AppUser.normalise(email))
                .<UserDetails>map(u -> new Account(u.getId(), u.getEmail(), u.getPasswordHash(), u.isConfirmed(),
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))))
                .orElseThrow(() -> new UsernameNotFoundException("no such user"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        // «Не пацьверджаны» правяраецца толькі пасьля правільнага пароля: інакш адказ выдаваў бы, што адрас
        // зарэгістраваны, любому, хто паспрабуе ўвайсьці з выпадковым паролем.
        provider.setPreAuthenticationChecks(user -> {
        });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("not confirmed");
            }
        });
        return new ProviderManager(provider);
    }
}
