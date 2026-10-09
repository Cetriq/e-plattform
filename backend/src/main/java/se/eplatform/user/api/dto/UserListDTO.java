package se.eplatform.user.api.dto;

import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserListDTO(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String displayName,
    String phone,
    List<String> roles,
    boolean active,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static UserListDTO from(User user) {
        List<String> roles = user.getRoles().stream()
            .map(r -> r.getName())
            .sorted()
            .collect(Collectors.toList());

        return new UserListDTO(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getFullName(),
            user.getPhone(),
            roles,
            user.isActive(),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }
}
