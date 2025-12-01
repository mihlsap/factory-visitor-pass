package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.auth.AuthResponse;
import com.fvps.backend.domain.dto.auth.LoginRequest;
import com.fvps.backend.domain.dto.auth.RegisterRequest;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.security.JwtService;
import com.fvps.backend.services.AuthService;
import com.fvps.backend.services.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final FileStorageService fileStorageService;

    @Override
    public AuthResponse register(RegisterRequest request, MultipartFile photo) {
        // 1. Zapisz zdjęcie
        String photoFilename = fileStorageService.savePhoto(photo);

        // 2. Stwórz obiekt User
        var user = User.builder()
                .name(request.getName())
                .surname(request.getSurname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // Hashowanie!
                .companyName(request.getCompanyName())
                .photoUrl(photoFilename)
                .role(UserRole.USER) // Domyślnie rejestrujemy USERa
                .status(UserStatus.ACTIVE)
                .build();

        // 3. Zapisz w bazie
        userRepository.save(user);

        // 4. Wygeneruj token i zwróć
        var jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .role(user.getRole())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 1. Spring Security sprawdza login i hasło
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // 2. Jeśli nie rzuciło wyjątkiem -> dane są poprawne. Pobieramy usera.
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        // 3. Generujemy token
        var jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .role(user.getRole())
                .build();
    }
}