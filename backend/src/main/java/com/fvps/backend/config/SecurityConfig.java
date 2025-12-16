package com.fvps.backend.config;

import com.fvps.backend.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Main security configuration class for the application.
 * <p>
 * This class configures Spring Security to use a stateless, JWT-based authentication mechanism.
 * It defines the security filter chain, password encoding strategy, and access control rules
 * for various API endpoints.
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${app.endpoints.verify}")
    private String verifyEndpoint;

    /**
     * Exposes the {@link AuthenticationManager} bean.
     * <p>
     * This bean is required by the {@link com.fvps.backend.services.impl.AuthServiceImpl} to programmatically
     * authenticate users (e.g., during login).
     * </p>
     *
     * @param config the authentication configuration.
     * @return the authentication manager.
     * @throws Exception if an error occurs while retrieving the manager.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configures the password encoder using BCrypt.
     * <p>
     * BCrypt is a strong hashing function that incorporates a salt to protect against rainbow table attacks.
     * It is used for hashing user passwords before storing them in the database.
     * </p>
     *
     * @return the BCrypt password encoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the security filter chain.
     * <p>
     * This method defines the core security rules:
     * <ul>
     * <li>Disables CSRF protection (as the API is stateless and uses JWT).</li>
     * <li>Configures URL authorization:
     * <ul>
     * <li>Public access: Auth endpoints, Swagger UI, Actuator health.</li>
     * <li>Admin only: {@code /api/admin/**} and sensitive Actuator endpoints.</li>
     * <li>Guard/Admin: Verification endpoint (read from properties).</li>
     * <li>Authenticated users: All other requests.</li>
     * </ul>
     * </li>
     * <li>Sets session management to {@link SessionCreationPolicy#STATELESS}, preventing server-side sessions.</li>
     * <li>Adds the custom {@link JwtFilter} before the standard {@link UsernamePasswordAuthenticationFilter}
     * to intercept and validate JWT tokens in incoming requests.</li>
     * </ul>
     * </p>
     *
     * @param http the HttpSecurity object to configure.
     * @return the built SecurityFilterChain.
     * @throws Exception if an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req -> req
                        .requestMatchers(
                                "/api/auth/**",
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(verifyEndpoint + "/**").hasAnyRole("GUARD", "ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}