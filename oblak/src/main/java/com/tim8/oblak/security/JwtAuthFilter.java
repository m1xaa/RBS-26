package com.tim8.oblak.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter koji za svaki request proverava Authorization: Bearer <token> header.
 * Ako je token validan, postavlja Authentication u SecurityContext sa
 * username-om kao principal-om i rolom kao authority (ROLE_USER ili ROLE_ADMIN).
 *
 * Ako tokena nema ili nije validan, filter ne baca gresku - prosledjuje
 * dalje, a SecurityConfig ce odbiti zahtev jer endpoint zahteva autentikaciju.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String username = claims.getSubject();
                String role = claims.get("role", String.class);

                var auth = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                // Nevazeci token - ostavljamo SecurityContext praznim,
                // SecurityConfig ce vratiti 401 za zasticene endpoint-e.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
