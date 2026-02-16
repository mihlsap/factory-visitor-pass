package com.fvps.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global configuration for Spring Web MVC.
 * <p>
 * This class handles web-layer-specific settings, primarily <b>CORS
 * (Cross-Origin Resource Sharing)</b>.
 * It ensures that the frontend application (running on a different origin/port)
 * is allowed
 * to consume the REST API securely.
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * The URL of the frontend application.
     * <p>
     * Injected from application properties (e.g., {@code http://localhost:5173} for
     * dev
     * or {@code https://my-app.com} for prod). This ensures the CORS policy is
     * dynamic
     * and environment-aware.
     * </p>
     */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Configures CORS mappings.
     * <p>
     * Allows requests from the configured frontend URL to all endpoints
     * ({@code /**}).
     * Supports standard REST methods and credential transmission (cookies/headers).
     * </p>
     *
     * @param registry the registry to add mappings to.
     */
    @Override
    public void addCorsMappings(@jakarta.annotation.Nonnull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}