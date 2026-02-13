package com.fvps.backend.security;

import com.fvps.backend.repositories.UserRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Service responsible for retrieving user identity data during the authentication process.
 * <p>
 * This class implements Spring Security's standard {@link UserDetailsService} interface.
 * It acts as a bridge between the security framework and the application's database (via {@link UserRepository}),
 * converting domain {@code User} entities into {@code UserDetails} objects that Spring Security can understand.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Locates the user based on the username.
     * <p>
     * In the context of this application, the <b>email address</b> serves as the unique username.
     * This method queries the database for the given email and, if found, wraps the result
     * in a {@link CustomUserDetails} adapter.
     * </p>
     *
     * @param username the username identifying the user whose data is required (here: the email).
     * @return a fully populated {@link UserDetails} object (never {@code null}).
     * @throws UsernameNotFoundException if the user could not be found with the provided email.
     */
    @Override
    @Nonnull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}