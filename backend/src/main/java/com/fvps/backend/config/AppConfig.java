package com.fvps.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Clock;
import java.util.Locale;

/**
 * Main application configuration class defining global beans.
 * <p>
 * This class is responsible for configuring fundamental Spring beans used throughout the application,
 * such as time handling (Clock), internationalization (MessageSource), and validation.
 * </p>
 */
@Configuration
public class AppConfig {

    /**
     * Creates a {@link Clock} bean using the system default time zone.
     * <p>
     * Injecting {@code Clock} instead of using {@code LocalDateTime.now()} directly allows for
     * easier testing of time-dependent logic (e.g., by mocking a fixed time in unit tests).
     * </p>
     *
     * @return the system default zone clock.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Configures the {@link MessageSource} for internationalization (i18n).
     * <p>
     * It loads messages from the {@code classpath:messages} file (e.g., {@code messages.properties}).
     * The default encoding is set to UTF-8 to support special characters.
     * </p>
     *
     * @return the configured message source.
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    /**
     * Configures the {@link LocalValidatorFactoryBean} to use the custom {@link MessageSource}.
     * <p>
     * This ensures that validation messages (e.g., from {@code @NotBlank}, {@code @Email})
     * are resolved using the same {@code messages.properties} file as the rest of the application.
     * </p>
     *
     * @return the validator factory bean linked with the message source.
     */
    @Bean
    public LocalValidatorFactoryBean getValidator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource());
        return bean;
    }

    /**
     * Defines the default {@link Locale} for the application based on configuration properties.
     * <p>
     * The locale code is read from the {@code app.messages.locale} property (e.g., "en-GB", "pl-PL").
     * This bean is used to set the default language for emails and notifications.
     * </p>
     *
     * @param localeCode the language tag string injected from application properties.
     * @return the Locale object corresponding to the provided code.
     */
    @Bean
    public Locale defaultLocale(@Value("${app.messages.locale}") String localeCode) {
        return Locale.forLanguageTag(localeCode);
    }
}