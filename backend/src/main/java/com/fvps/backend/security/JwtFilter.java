package com.fvps.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A custom security filter that executes once per request to validate JWT
 * tokens.
 * <p>
 * This filter sits in the Spring Security filter chain. It inspects incoming
 * HTTP requests
 * for the presence of a valid JSON Web Token (JWT) in the "Authorisation"
 * header.
 * If a valid token is found, it authenticates the user for the duration of the
 * request
 * by populating the {@link SecurityContextHolder}.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * The core filtering logic executed for every request.
     * <p>
     * The workflow is as follows:
     * <ol>
     * <li>Check if the request has an "Authorisation" header starting with
     * "Bearer".</li>
     * <li>If not, proceed down the filter chain without authentication (allowing
     * public endpoints to work).</li>
     * <li>Extract the JWT and the username (email) from the token.</li>
     * <li>If the user is not already authenticated in the current context:
     * <ul>
     * <li>Load the user details from the database (to ensure the user still exists
     * and hasn't been banned).</li>
     * <li>Validate the token signature and expiration.</li>
     * <li>If valid, create an {@link UsernamePasswordAuthenticationToken} and set
     * it in the context.</li>
     * </ul>
     * </li>
     * <li>Continue the filter chain.</li>
     * </ol>
     * </p>
     *
     * @param request     the incoming HTTP request.
     * @param response    the outgoing HTTP response.
     * @param filterChain the chain of filters to proceed to.
     * @throws ServletException if a servlet error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Check for Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract token and username
        jwt = authHeader.substring(7); // Skip "Bearer "
        userEmail = jwtService.extractUsername(jwt);

        // 3. Validate and Authenticate
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Load user from DB to verify status (e.g. not blocked since token issue)
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                // 4. Set the authentication in the context
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}