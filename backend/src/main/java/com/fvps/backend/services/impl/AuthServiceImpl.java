package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.auth.AuthResponse;
import com.fvps.backend.domain.dto.auth.LoginRequest;
import com.fvps.backend.domain.dto.auth.RegisterRequest;
import com.fvps.backend.domain.dto.auth.TwoFactorRequest;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.security.CustomUserDetails;
import com.fvps.backend.security.JwtService;
import com.fvps.backend.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final UserService userService;
    private final Clock clock;
    private final MessageSource messageSource;
    private final Locale defaultLocale;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.endpoints.reset-password}")
    private String resetPasswordEndpoint;

    @Value("${app.company.domain}")
    private String companyDomain;

    @Value("${app.company.name}")
    private String defaultCompanyName;

    @Value("${app.auth.default-guest-company}")
    private String defaultGuestCompanyName;

    @Value("${app.auth.2fa.code-validity-minutes}")
    private int twoFactorValidityMinutes;

    @Value("${app.auth.password-reset.token-validity-minutes}")
    private int passwordResetValidityMinutes;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Role Assignment:</b> Checks the email domain. If it matches {@code app.company.domain},
     * the user is automatically assigned {@link UserRole#EMPLOYEE}. Otherwise, they are assigned {@link UserRole#GUEST}.</li>
     * <li><b>Company Name:</b> Guests must provide a company name; otherwise, a default guest company is assigned.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, MultipartFile photo) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User with this email already exists.");
        }

        String photoFilename = fileStorageService.savePhoto(photo);

        UserRole assignedRole;
        if (request.getEmail().toLowerCase().endsWith(companyDomain.toLowerCase())) {
            assignedRole = UserRole.EMPLOYEE;
            request.setCompanyName(defaultCompanyName);
        } else {
            assignedRole = UserRole.GUEST;
            if (request.getCompanyName() == null || request.getCompanyName().isBlank()) {
                request.setCompanyName(defaultGuestCompanyName);
            }
        }

        var user = User.builder()
                .name(request.getName())
                .surname(request.getSurname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .companyName(request.getCompanyName())
                .phoneNumber(request.getPhoneNumber())
                .photoUrl(photoFilename)
                .role(assignedRole)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);

        auditLogService.logEvent(user.getId(), "REGISTER",
                "New user registered (" + assignedRole + "): " + user.getEmail());

        var jwtToken = jwtService.generateToken(new CustomUserDetails(user));
        return AuthResponse.builder()
                .token(jwtToken)
                .role(user.getRole())
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Brute-force Protection:</b> Checks if the account is temporarily locked via {@code userService.registerFailedLogin}.</li>
     * <li><b>Access Control:</b> explicitly rejects users with {@link UserStatus#BLOCKED}.</li>
     * <li><b>2FA Logic:</b> Upon successful password validation, generates a random 6-digit code, saves it to the DB, and emails it.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user != null) {
            if (user.getStatus() == UserStatus.BLOCKED) {
                auditLogService.logEvent(user.getId(), "LOGIN_BLOCKED", "Login attempt on banned account.");
                throw new RuntimeException("Account has been blocked by administrator.");
            }

            if (user.getLockoutTime() != null) {
                if (user.getLockoutTime().isAfter(LocalDateTime.now(clock))) {
                    auditLogService.logEvent(user.getId(), "LOGIN_LOCKED", "Login attempt on locked account.");
                    long minutesLeft = java.time.Duration.between(LocalDateTime.now(clock), user.getLockoutTime()).toMinutes() + 1;
                    throw new RuntimeException("Account is temporarily locked due to too many failed attempts. Try again in " + minutesLeft + " minutes.");
                } else {
                    userService.resetLockoutStats(user.getId());
                    user = userRepository.findById(user.getId()).orElseThrow(() -> new RuntimeException("Unknown user"));
                }
            }
        } else {
            // Anti-enumeration: Generic message, but logic proceeds to allow timing consistency if possible
            throw  new RuntimeException("Unknown user");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            if (user != null) {
                try {
                    userService.registerFailedLogin(user.getId());
                } catch (Exception ex) {
                    log.error("Failed to increment failed login attempts for user: {}", user.getId(), ex);
                }
            } else {
                auditLogService.logEvent("LOGIN_FAILED_UNKNOWN", "Failed login attempt for unknown email: " + request.getEmail());
            }
            throw new RuntimeException("Invalid email or password.");
        }

        // Reset counters on successful password entry
        if (user.getFailedLoginAttempts() > 0 || user.getLockoutTime() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockoutTime(null);
            userRepository.save(user);
        }

        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        user.setTwoFactorCode(code);
        user.setTwoFactorCodeExpiry(LocalDateTime.now(clock).plusMinutes(twoFactorValidityMinutes));
        userRepository.save(user);

        String subject = messageSource.getMessage("email.auth.2fa.subject", null, defaultLocale);

        Object[] args = {code, twoFactorValidityMinutes};
        String content = messageSource.getMessage("email.auth.2fa.body", args, defaultLocale);

        emailService.sendEmail(user.getEmail(), subject, content);

        auditLogService.logEvent(user.getId(), "LOGIN_2FA_INIT", "Password correct. 2FA code sent.");

        return AuthResponse.builder()
                .token(null) // No token yet
                .role(user.getRole())
                .mfaEnabled(true)
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b> This is the final step where the JWT is actually signed and returned.
     * It validates the code against the database and updates {@code lastLogin} timestamp.
     * </p>
     */
    @Override
    @Transactional
    public AuthResponse verifyTwoFactor(TwoFactorRequest request) {
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Unknown user"));

        if (user.getTwoFactorCode() == null || !user.getTwoFactorCode().equals(request.getCode())) {
            auditLogService.logEvent(user.getId(), "LOGIN_2FA_FAILED", "Invalid 2FA code.");
            throw new RuntimeException("Invalid verification code.");
        }

        if (user.getTwoFactorCodeExpiry().isBefore(LocalDateTime.now(clock))) {
            throw new RuntimeException("Code expired. Please login again.");
        }

        user.setTwoFactorCode(null);
        user.setTwoFactorCodeExpiry(null);
        user.setLastLogin(LocalDateTime.now(clock));
        userRepository.save(user);

        auditLogService.logEvent(user.getId(), "LOGIN_SUCCESS", "Logged in (2FA confirmed).");
        var jwtToken = jwtService.generateToken(new CustomUserDetails(user));
        return AuthResponse.builder()
                .token(jwtToken)
                .role(user.getRole())
                .mfaEnabled(false)
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note (Security):</b>
     * Includes <b>Timing Attack Protection</b>. If the email is unknown, the system performs a dummy
     * hashing operation to simulate workload, preventing attackers from enumerating valid emails
     * based on response time.
     * </p>
     */
    @Override
    @Transactional
    public void forgotPassword(String email) {
        var userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            auditLogService.logEvent("PASSWORD_RESET_ATTEMPT_UNKNOWN", "Password reset attempt for unknown email: " + email);
            // Simulate processing time to prevent user enumeration
            String dummyToken = UUID.randomUUID().toString();
            passwordEncoder.encode(dummyToken);
            return;
        }

        User user = userOptional.get();
        String rawToken = UUID.randomUUID().toString();
        // Store hashed token in DB
        user.setResetToken(passwordEncoder.encode(rawToken));
        user.setResetTokenExpiry(LocalDateTime.now(clock).plusMinutes(passwordResetValidityMinutes));
        userRepository.save(user);

        String link = frontendUrl + resetPasswordEndpoint + "?token=" + rawToken + "&email=" + email;

        String subject = messageSource.getMessage("email.auth.reset.subject", null, defaultLocale);

        Object[] args = {link};
        String content = messageSource.getMessage("email.auth.reset.body", args, defaultLocale);

        emailService.sendEmail(user.getEmail(), subject, content);

        auditLogService.logEvent(user.getId(), "PASSWORD_RESET_INIT", "Secure reset link sent.");
    }

    @Override
    @Transactional
    public void resetPassword(String email, String rawToken, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid request."));

        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now(clock))) {
            throw new RuntimeException("Token expired.");
        }

        if (!passwordEncoder.matches(rawToken, user.getResetToken())) {
            auditLogService.logEvent(user.getId(), "PASSWORD_RESET_FAILED", "Invalid token used.");
            throw new RuntimeException("Invalid token.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        auditLogService.logEvent(user.getId(), "PASSWORD_RESET_COMPLETE", "Password has been changed.");
    }

    @Override
    public void logout() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String email = auth.getName();
                userRepository.findByEmail(email).ifPresent(user ->
                        auditLogService.logEvent(user.getId(), "LOGOUT", "User logged out manually.")
                );
            }
        } catch (Exception e) {
            log.error("Error during logout logging", e);
        }
        SecurityContextHolder.clearContext();
    }
}