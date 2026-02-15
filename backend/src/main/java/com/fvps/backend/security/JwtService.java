package com.fvps.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service responsible for managing JSON Web Token (JWT) operations.
 * <p>
 * This class handles the creation (signing), parsing, and validation of JWTs.
 * It uses the HMAC SHA-256 algorithm to ensure token integrity.
 * The tokens generated here are used to authenticate stateless API requests.
 * </p>
 */
@Service
public class JwtService {

    /**
     * The secret key used for signing and verifying tokens.
     * <p>
     * Injected from {@code application.security.jwt.secret-key}.
     * Must be a Base64-encoded string sufficiently long to support HS256 (at least 256 bits).
     * </p>
     */
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    /**
     * The validity duration of the token in milliseconds.
     * <p>
     * Injected from {@code application.security.jwt.expiration}.
     * Example: 86.4 million for 24 hours.
     * </p>
     */
    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    /**
     * Extracts the username (subject) from the JWT token.
     *
     * @param token the JWT token string.
     * @return the username (email) stored in the token's subject claim.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generic method to retrieve a specific claim from the token.
     *
     * @param token          the JWT token string.
     * @param claimsResolver a function to extract the desired type from the Claims object.
     * @param <T>            the type of the claim being extracted.
     * @return the extracted claim value.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generates a fresh JWT token for the provided user details.
     * <p>
     * Uses default (empty) extra claims.
     * </p>
     *
     * @param userDetails the user for whom the token is generated.
     * @return the signed JWT string.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a token with additional custom claims.
     *
     * @param extraClaims a map of custom key-value pairs to include in the payload.
     * @param userDetails the user for whom the token is generated.
     * @return the signed JWT string.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Builds the JWT structure, sets claims, dates, and signs it.
     *
     * @param extraClaims custom claims.
     * @param userDetails user details (username is set as a subject).
     * @param expiration  expiration time in milliseconds.
     * @return the compact URL-safe JWT string.
     */
    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates the token against the user details.
     * <p>
     * Checks two conditions:
     * <ol>
     * <li>The username extracted from the token matches the provided UserDetails.</li>
     * <li>The token has not expired.</li>
     * </ol>
     * </p>
     *
     * @param token       the JWT token string.
     * @param userDetails the user details loaded from the database.
     * @return true if the token is valid and belongs to the user; false otherwise.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Checks if the token has passed its expiration date.
     *
     * @param token the JWT token string.
     * @return true if the current date is after the expiration date.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from the token.
     *
     * @param token the JWT token string.
     * @return the expiration date.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parses the token string to retrieve the payload (Claims).
     * <p>
     * This method verifies the signature using the secret key. If the signature is invalid
     * or the token is malformed, the JJWT library will throw a runtime exception.
     * </p>
     *
     * @param token the JWT token string.
     * @return the Claims object containing the payload.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Decodes the Base64-encoded secret key into a cryptographic Key object.
     *
     * @return the HMAC-SHA key used for signing.
     */
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}