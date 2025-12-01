package com.fvps.backend.domain.entities;

import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User implements UserDetails { // Implementacja UserDetails potrzebna dla Spring Security

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String surname;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    // Pola opcjonalne (tylko dla USER)
    private String photoUrl;
    private String companyName;

    // Reset hasła
    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    // Pola audytowe
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = UserStatus.ACTIVE; // Domyślnie aktywny
        }
    }

    // --- Metody z interfejsu UserDetails (Dla Spring Security) ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.BLOCKED; // Tu jest nasza blokada!
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.DELETED; // Deleted userzy są wyłączeni
    }
}