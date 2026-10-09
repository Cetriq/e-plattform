package se.eplatform.security;

import org.junit.jupiter.api.Test;
import se.eplatform.common.security.TokenService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-0123456789";

    @Test
    void issuedTokenVerifiesToTheSameUser() {
        TokenService tokens = new TokenService(SECRET, "eplatform", 60);
        UUID userId = UUID.randomUUID();

        assertThat(tokens.verify(tokens.issue(userId))).contains(userId);
    }

    @Test
    void expiredTokenIsRejected() {
        TokenService tokens = new TokenService(SECRET, "eplatform", -1);

        assertThat(tokens.verify(tokens.issue(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        TokenService other = new TokenService("another-secret-that-is-long-enough-0123456789", "eplatform", 60);
        TokenService tokens = new TokenService(SECRET, "eplatform", 60);

        assertThat(tokens.verify(other.issue(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        TokenService other = new TokenService(SECRET, "someone-else", 60);
        TokenService tokens = new TokenService(SECRET, "eplatform", 60);

        assertThat(tokens.verify(other.issue(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        TokenService tokens = new TokenService(SECRET, "eplatform", 60);

        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify("abc.def.ghi")).isEmpty();
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new TokenService("too-short", "eplatform", 60))
                .isInstanceOf(IllegalStateException.class);
    }
}
