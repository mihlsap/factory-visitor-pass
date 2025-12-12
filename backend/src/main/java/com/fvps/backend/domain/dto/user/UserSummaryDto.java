package com.fvps.backend.domain.dto.user;

import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
    private UUID id;
    private String email;
    private String name;
    private String surname;
    private String companyName;
    private UserRole role;
    private UserStatus status;
    private String photoUrl;
    private String phoneNumber;
    private Long version;
}