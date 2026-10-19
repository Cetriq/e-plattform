package se.eplatform.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies signed (HS256) access tokens.
 *
 * Tokens are stateless, so they survive restarts and work across several
 * backend instances. Roles are not stored in the token; the user is loaded
 * from the database on each request so role changes and deactivation apply
 * immediately. The claim "org" holds the organisation staff are working for;
 * membership is checked again on every request.
 */
@Service
public class TokenService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String ORGANIZATION_CLAIM = "org";

    private final byte[] secret;
    private final String issuer;
    private final long expirationSeconds;

    public TokenService(
            @Value("${eplatform.security.jwt.secret}") String secret,
            @Value("${eplatform.security.jwt.issuer:eplatform}") String issuer,
            @Value("${eplatform.security.jwt.expiration:3600}") long expirationSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "eplatform.security.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    /** The verified contents of a token. organizationId is null for users without an active organisation. */
    public record Claims(UUID userId, UUID organizationId) {}

    public String issue(UUID userId) {
        return issue(userId, null);
    }

    public String issue(UUID userId, UUID organizationId) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expirationSeconds)))
                .jwtID(UUID.randomUUID().toString());
        if (organizationId != null) {
            builder.claim(ORGANIZATION_CLAIM, organizationId.toString());
        }
        JWTClaimsSet claims = builder.build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign token", e);
        }
    }

    /**
     * Returns the claims of a valid token, or empty if the token is malformed,
     * has a bad signature, is from another issuer or has expired.
     */
    public Optional<Claims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (!issuer.equals(claims.getIssuer())
                    || expiration == null
                    || expiration.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            String organization = claims.getStringClaim(ORGANIZATION_CLAIM);
            return Optional.of(new Claims(UUID.fromString(claims.getSubject()),
                    organization == null ? null : UUID.fromString(organization)));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
