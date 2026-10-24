package io.github.heorhipuhachou.bmuz.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Правы ўвайшоўшага чытаюцца з базы на кожным запыце, а не толькі пры ўваходзе: калі суперадміністратар забраў або
 * даў правы, яны мяняюцца адразу, а не праз 8 гадзін сэсіі. Выдалены або непацьверджаны запіс выходзіць з сэсіі.
 * Зьмена адраса таксама заканчвае сэсію: стары адрас больш нікому не належыць або належыць ужо іншаму запісу.
 * Наведнікі без уваходу сюды не трапляюць, таму запыт да базы — толькі для тых, хто ўвайшоў.
 */
class FreshRolesFilter extends OncePerRequestFilter {

    private final UserDetailsService users;
    private final SecurityContextRepository contexts;

    FreshRolesFilter(UserDetailsService users, SecurityContextRepository contexts) {
        this.users = users;
        this.contexts = contexts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken && auth.isAuthenticated()) {
            UserDetails fresh = load(auth.getName());
            if (fresh == null || !fresh.isEnabled() || !sameAccount(auth.getPrincipal(), fresh)) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            } else if (!Set.copyOf(fresh.getAuthorities()).equals(Set.copyOf(auth.getAuthorities()))) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(context);
                contexts.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }

    /** Адрас мог перайсьці да іншага запісу: сэсія належыць нумару запісу, а не адрасу. */
    private static boolean sameAccount(Object principal, UserDetails fresh) {
        return principal instanceof Account a && fresh instanceof Account f && a.getId() == f.getId();
    }

    private UserDetails load(String email) {
        try {
            return users.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }
}
