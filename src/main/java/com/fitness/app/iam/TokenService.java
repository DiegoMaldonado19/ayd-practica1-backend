package com.fitness.app.iam;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.UserRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies the session token. The role travels signed inside it. */
@Slf4j
@Service
public class TokenService
{
    private static final String ROLE_CLAIM    = "role";
    private static final String USER_ID_CLAIM = "user_id";

    private final SecretKey secretKey;
    private final Duration  accessDuration;

    public TokenService(@Value("${app.security.jwt.secret}")         String secret,
                        @Value("${app.security.jwt.access-minutes}") long   accessMinutes)
    {
        this.secretKey      = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessDuration = Duration.ofMinutes(accessMinutes);
    }

    public String issue(AppUser user)
    {
        var issuedAt  = Instant.now();
        var expiresAt = issuedAt.plus(accessDuration);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim(ROLE_CLAIM, user.getRole().name())
                // As text on purpose: a JSON number comes back as Integer or Long
                // depending on its size, and that ambiguity is not worth carrying.
                .claim(USER_ID_CLAIM, user.getAppUserId().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public long expiresInSeconds()
    {
        return accessDuration.toSeconds();
    }

    /** Empty when the token is absent, tampered with or expired: the filter then leaves the request anonymous. */
    public Optional<AuthenticatedUser> verify(String token)
    {
        try
        {
            var claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new AuthenticatedUser(Long.valueOf(claims.get(USER_ID_CLAIM, String.class)),
                                                     claims.getSubject(),
                                                     UserRole.valueOf(claims.get(ROLE_CLAIM, String.class))));
        }
        catch (JwtException | IllegalArgumentException ex)
        {
            log.debug("Rejected token: {}", ex.getMessage(), ex);

            return Optional.empty();
        }
    }
}
