package se.eplatform.auth.dto;

import java.util.Set;

public record AuthResponse(
    String token,
    UserInfo user
) {
    public record UserInfo(
        String id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        Set<String> roles,
        Set<String> permissions
    ) {}
}
