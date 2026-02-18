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
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.EmailService;
import com.fvps.backend.services.FileStorageService;
import com.fvps.backend.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private FileStorageService fileStorageService;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;
    @Mock private UserService userService;
    @Mock private Clock clock;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private AuthServiceImpl authService;

    private final String email = "test@fvps.com";
    private final String password = "Password123!";
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "companyDomain", "@fvps.com");
        ReflectionTestUtils.setField(authService, "defaultCompanyName", "FVPS Corp");
        ReflectionTestUtils.setField(authService, "defaultGuestCompanyName", "Guest");
        ReflectionTestUtils.setField(authService, "twoFactorValidityMinutes", 5);
        ReflectionTestUtils.setField(authService, "passwordResetValidityMinutes", 15);
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(authService, "resetPasswordEndpoint", "/reset");

        Instant fixedTime = Instant.parse("2025-01-01T12:00:00.00Z");
        lenient().when(clock.instant()).thenReturn(fixedTime);
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));

        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Message");

        user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("encoded_password")
                .role(UserRole.EMPLOYEE)
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
    }

    @Test
    void shouldLoginSuccessfully_andTrigger2FA() {
        LoginRequest request = new LoginRequest(email, password);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(request);

        assertTrue(response.isMfaEnabled());
        assertNull(response.getToken());
        assertNotNull(user.getTwoFactorCode());
        assertNotNull(user.getTwoFactorCodeExpiry());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(emailService).sendEmail(eq(email), anyString(), anyString());
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    void shouldThrowException_whenAccountIsBlocked() {
        user.setStatus(UserStatus.BLOCKED);
        LoginRequest request = new LoginRequest(email, password);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertEquals("Account has been blocked by administrator.", ex.getMessage());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void shouldThrowException_whenAccountIsLockedTemporary() {
        user.setLockoutTime(LocalDateTime.now(clock).plusMinutes(10));
        LoginRequest request = new LoginRequest(email, password);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("Account is temporarily locked"));
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void shouldRegisterFailedLogin_whenBadCredentials() {
        LoginRequest request = new LoginRequest(email, "WrongPass");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        doThrow(new BadCredentialsException("Bad creds"))
                .when(authenticationManager).authenticate(any());

        assertThrows(RuntimeException.class, () -> authService.login(request));

        verify(userService).registerFailedLogin(user.getId());
    }

    @Test
    void shouldRegisterUser_withCompanyRole_whenEmailDomainMatches() {
        RegisterRequest request = RegisterRequest.builder()
                .email("employee@fvps.com")
                .password(password)
                .name("John").surname("Doe")
                .build();

        MultipartFile photo = mock(MultipartFile.class);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(fileStorageService.savePhoto(photo)).thenReturn("photo.jpg");
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("jwt_token");

        AuthResponse response = authService.register(request, photo);

        assertNotNull(response.getToken());
        assertEquals(UserRole.EMPLOYEE, response.getRole());
        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("employee@fvps.com") &&
                        u.getRole() == UserRole.EMPLOYEE &&
                        u.getCompanyName().equals("FVPS Corp")
        ));
    }

    @Test
    void shouldRegisterUser_withGuestRole_whenEmailDomainIsExternal() {
        RegisterRequest request = RegisterRequest.builder()
                .email("guest@gmail.com")
                .password(password)
                .name("Jan").surname("Nowak")
                .build();

        MultipartFile photo = mock(MultipartFile.class);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(fileStorageService.savePhoto(photo)).thenReturn("photo.jpg");
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("jwt_token");

        AuthResponse response = authService.register(request, photo);

        assertEquals(UserRole.GUEST, response.getRole());
        verify(userRepository).save(argThat(u ->
                u.getRole() == UserRole.GUEST &&
                        u.getCompanyName().equals("Guest")
        ));
    }

    @Test
    void shouldThrowException_whenRegisteringExistingEmail() {
        RegisterRequest request = RegisterRequest.builder().email(email).build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> authService.register(request, null));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldVerify2FA_whenCodeIsCorrect() {
        String code = "123456";
        user.setTwoFactorCode(code);
        user.setTwoFactorCodeExpiry(LocalDateTime.now(clock).plusMinutes(5));

        TwoFactorRequest request = new TwoFactorRequest();
        request.setEmail(email);
        request.setCode(code);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("access_token");

        AuthResponse response = authService.verifyTwoFactor(request);

        assertNotNull(response.getToken());
        assertFalse(response.isMfaEnabled());
        assertNull(user.getTwoFactorCode());
        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowException_when2FACodeIsInvalid() {
        user.setTwoFactorCode("123456");
        user.setTwoFactorCodeExpiry(LocalDateTime.now(clock).plusMinutes(5));

        TwoFactorRequest request = new TwoFactorRequest();
        request.setEmail(email);
        request.setCode("000000");

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        Exception ex = assertThrows(RuntimeException.class, () -> authService.verifyTwoFactor(request));
        assertEquals("Invalid verification code.", ex.getMessage());
        verify(auditLogService).logEvent(eq(user.getId()), eq("LOGIN_2FA_FAILED"), anyString());
    }

    @Test
    void shouldResetPassword_whenTokenIsValid() {
        String rawToken = "valid-token";
        String newPassword = "NewPassword123!";
        user.setResetToken("encoded-token");
        user.setResetTokenExpiry(LocalDateTime.now(clock).plusMinutes(10));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawToken, "encoded-token")).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded-new-pass");

        authService.resetPassword(email, rawToken, newPassword);

        verify(userRepository).save(user);
        assertNull(user.getResetToken());
        verify(auditLogService).logEvent(eq(user.getId()), eq("PASSWORD_RESET_COMPLETE"), anyString());
    }

    @Test
    void shouldThrowException_whenResetTokenExpired() {
        user.setResetTokenExpiry(LocalDateTime.now(clock).minusMinutes(1));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () -> authService.resetPassword(email, "token", "pass"));
        verify(userRepository, never()).save(any());
    }
}