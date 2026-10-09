package se.eplatform.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import se.eplatform.auth.dto.AuthResponse.UserInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * Access to the logged-in user. Controllers must take the acting user from
 * here, never from request parameters or bodies.
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<UserInfo> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserInfo user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * The logged-in user, or 401 if there is none.
     */
    public static UserInfo require() {
        return get().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inloggning krävs"));
    }

    public static UUID requireId() {
        return UUID.fromString(require().id());
    }

    /**
     * Case handling staff (handläggare and administrators) can see all cases.
     */
    public static boolean isStaff(UserInfo user) {
        return user.roles().contains("ADMIN") || user.roles().contains("MANAGER");
    }

    /**
     * The logged-in staff user, or 401/403.
     */
    public static UserInfo requireStaff() {
        UserInfo user = require();
        if (!isStaff(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kräver handläggarbehörighet");
        }
        return user;
    }
}
