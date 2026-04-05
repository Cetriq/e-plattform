package se.eplatform.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.auth.dto.AuthResponse;
import se.eplatform.auth.dto.AuthResponse.UserInfo;
import se.eplatform.user.domain.User;
import se.eplatform.user.repository.UserRepository;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock authentication service for development.
 * In production, this would be replaced with BankID or other e-legitimation.
 */
@Service
public class MockAuthService {

    private final UserRepository userRepository;
    private final ConcurrentHashMap<String, String> tokenToUserId = new ConcurrentHashMap<>();

    public MockAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Mock login - in development, any password works.
     * Looks up user by email and returns auth token.
     */
    @Transactional
    public Optional<AuthResponse> login(String email, String password) {
        return userRepository.findByEmail(email)
            .filter(User::isActive)
            .map(user -> {
                // Update last login
                user.setLastLoginAt(Instant.now());
                userRepository.save(user);

                // Generate mock token
                String token = generateToken(user);
                tokenToUserId.put(token, user.getId().toString());

                return new AuthResponse(token, toUserInfo(user));
            });
    }

    /**
     * Validate token and return user info.
     */
    @Transactional(readOnly = true)
    public Optional<AuthResponse> validateToken(String token) {
        String userId = tokenToUserId.get(token);
        if (userId == null) {
            return Optional.empty();
        }

        return userRepository.findById(UUID.fromString(userId))
            .filter(User::isActive)
            .map(user -> new AuthResponse(token, toUserInfo(user)));
    }

    /**
     * Logout - invalidate token.
     */
    public void logout(String token) {
        tokenToUserId.remove(token);
    }

    /**
     * Get current user from token.
     */
    @Transactional(readOnly = true)
    public Optional<User> getCurrentUser(String token) {
        String userId = tokenToUserId.get(token);
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(UUID.fromString(userId));
    }

    private String generateToken(User user) {
        String payload = user.getId() + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID();
        return Base64.getEncoder().encodeToString(payload.getBytes());
    }

    private UserInfo toUserInfo(User user) {
        Set<String> roles = user.getRoles().stream()
            .map(r -> r.getName())
            .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
            .flatMap(r -> r.getPermissions().stream())
            .collect(Collectors.toSet());

        return new UserInfo(
            user.getId().toString(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getFullName(),
            roles,
            permissions
        );
    }
}
