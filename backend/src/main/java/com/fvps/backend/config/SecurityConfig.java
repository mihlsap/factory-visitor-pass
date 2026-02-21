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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Main security configuration class for the application.
 * <p>
 * This class configures Spring Security to use a stateless, JWT-based
 * authentication mechanism.
 * It defines the security filter chain, password encoding strategy,
 * Cross-Origin Resource Sharing (CORS)
 * policies, and access control rules for various API endpoints.
 * </p>
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${app.endpoints.verify}")
    private String verifyEndpoint;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Exposes the {@link AuthenticationManager} bean.
     * <p>
     * This bean is required by the
     * {@link com.fvps.backend.services.impl.AuthServiceImpl} to programmatically
     * authenticate users (e.g., during login).
     * </p>
     *
     * @param config the authentication configuration.
     * @return the authentication manager.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    /**
     * Configures the password encoder using BCrypt.
     * <p>
     * BCrypt is a strong hashing function that incorporates a salt to protect
     * against rainbow table attacks.
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
     * <li>Enables CORS using the {@link #corsConfigurationSource()} bean.</li>
     * <li>Disables CSRF protection (as the API is stateless and uses JWT).</li>
     * <li>Configures URL authorization:
     * <ul>
     * <li>Public access: Auth endpoints, Swagger UI, Actuator health.</li>
     * <li>Admin only: {@code /api/admin/**} and sensitive Actuator endpoints.</li>
     * <li>Guard/Admin: Verification endpoint (configured via properties).</li>
     * <li>Authenticated users: All other requests.</li>
     * </ul>
     * </li>
     * <li>Sets session management to {@link SessionCreationPolicy#STATELESS},
     * preventing server-side sessions.</li>
     * <li>Adds the custom {@link JwtFilter} before the standard
     * {@link UsernamePasswordAuthenticationFilter}
     * to intercept and validate JWT tokens in incoming requests.</li>
     * </ul>
     * </p>
     *
     * @param http the HttpSecurity object to configure.
     * @return the built SecurityFilterChain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req -> req
                        .requestMatchers(
                                "/api/auth/**",
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/api/uploads/**")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(verifyEndpoint + "/**").hasRole("GUARD")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures the Cross-Origin Resource Sharing (CORS) settings.
     * <p>
     * This bean defines the rules for accepting requests from different origins
     * (specifically the frontend).
     * It allows:
     * <ul>
     * <li>Requests from the configured frontend URL
     * ({@code app.frontend-url}).</li>
     * <li>Standard HTTP methods (GET, POST, PUT, DELETE, OPTIONS, PATCH).</li>
     * <li>All HTTP headers.</li>
     * <li>Credential transmission (cookies/headers), which is required for secure
     * authentication.</li>
     * </ul>
     * </p>
     *
     * @return the configured {@link CorsConfigurationSource}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}