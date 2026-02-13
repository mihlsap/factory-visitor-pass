package com.fvps.backend.security;

import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security adapter for the domain {@link User} entity.
 * <p>
 * This class implements the standard {@link UserDetails} interface, allowing
 * the application's
 * custom {@code User} entity to be understood and used by the Spring Security
 * framework
 * for authentication and authorisation.
 * </p>
 * <p>
 * Implemented as a Java {@code record} to ensure immutability of the security
 * context principal.
 * </p>
 *
 * @param user the domain user entity containing credentials and profile data.
 */
public record CustomUserDetails(User user) implements UserDetails {

    /**
     * Returns the authorities granted to the user.
     * <p>
     * Converts the domain {@link com.fvps.backend.domain.enums.UserRole} into a
     * Spring Security
     * {@link GrantedAuthority}. The prefix "ROLE_" is appended automatically to
     * comply with
     * standard Spring Security voting rules (e.g. needed for
     * {@code .hasRole("ADMIN")}).
     * </p>
     *
     * @return a collection containing the user's role.
     */
    @Override
    @jakarta.annotation.Nonnull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /**
     * Returns the password used to authenticate the user.
     *
     * @return the bcrypt-hashed password from the database.
     */
    @Override
    @jakarta.annotation.Nonnull
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Returns the username used to authenticate the user.
     * <p>
     * In this application, the unique identifier is the user's email address.
     * </p>
     *
     * @return the user's email.
     */
    @Override
    @jakarta.annotation.Nonnull
    public String getUsername() {
        return user.getEmail();
    }

    /**
     * Indicates whether the user's account has expired.
     * <p>
     * Currently hardcoded to {@code true} (never expires). Logic can be added here
     * if
     * temporary accounts are introduced in the future.
     * </p>
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user is locked or unlocked.
     * <p>
     * Maps to the {@link UserStatus#BLOCKED} status. If an admin has blocked the
     * user,
     * this method returns {@code false}, preventing login.
     * </p>
     */
    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.BLOCKED;
    }

    /**
     * Indicates whether the user's credentials (password) have expired.
     * <p>
     * Currently hardcoded to {@code true}. Can be connected to a password rotation
     * policy later.
     * </p>
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user is enabled or disabled.
     * <p>
     * Maps to the {@link UserStatus#DELETED} status. If the user has been
     * soft-deleted,
     * this method returns {@code false}, effectively disabling the account.
     * </p>
     */
    @Override
    public boolean isEnabled() {
        return user.getStatus() != UserStatus.DELETED;
    }
}