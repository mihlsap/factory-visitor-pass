package com.fvps.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * The main entry point for the Factory Visitor Pass System (FVPS) backend application.
 * <p>
 * This class bootstraps the Spring Boot application and enables key global configurations:
 * <ul>
 * <li>{@link EnableAsync} - Allows methods annotated with {@code @Async} to run in background threads (e.g., sending emails).</li>
 * <li>{@link EnableRetry} - Enables Spring Retry functionality (e.g. retrying failed database or network operations).</li>
 * <li>{@link EnableScheduling} - Activates the background task scheduler (used for expiration checks).</li>
 * <li>{@link EnableSpringDataWebSupport} - Configures how Spring Data types (like {@code Page}) are serialized to JSON.
 * Using {@code VIA_DTO} ensures stable and frontend-friendly pagination responses.</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class BackendApplication {

    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments passed during startup.
     */
    static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}