package com.fvps.backend.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @Schema(description = "Imię użytkownika", example = "Jan", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "First name is required")
    private String name;

    @Schema(description = "Nazwisko użytkownika", example = "Kowalski", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Last name is required")
    private String surname;

    @Schema(description = "Adres email (unikalny w systemie)", example = "jan.kowalski@fvps.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(
            description = "Hasło (min. 8 znaków, 1 duża litera, 1 mała litera, 1 cyfra, 1 znak specjalny)",
            example = "StrongPass1!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
            message = "Password must be at least 8 characters long, contain uppercase and lowercase letter, digit and special character."
    )
    private String password;

    @Schema(
            description = "Nazwa firmy. Dla pracowników wewnętrznych (domena @fvps.com) jest nadpisywana automatycznie.",
            example = "External Logistics Sp. z o.o."
    )
    private String companyName;

    @Schema(
            description = "Numer telefonu w formacie E.164",
            example = "+48123456789"
    )
    @Pattern(
            regexp = "^\\+[0-9]{6,15}$",
            message = "Invalid phone number format. Use international E.164 format (e.g., +48123456789)"
    )
    private String phoneNumber;
}