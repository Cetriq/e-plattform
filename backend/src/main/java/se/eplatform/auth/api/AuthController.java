package se.eplatform.auth.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.auth.dto.LoginRequest;
import se.eplatform.auth.service.MockAuthService;

import java.util.Map;

/**
 * Authentication controller for mock login.
 * Public endpoints - no authentication required.
 */
@RestController
@RequestMapping("/api/v1/public/auth")
public class AuthController {

    private final MockAuthService authService;

    public AuthController(MockAuthService authService) {
        this.authService = authService;
    }

    /**
     * Login with email (mock - any password works in dev).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password())
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials or user not found")));
    }

    /**
     * Validate token and get current user info.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "No valid token provided"));
        }

        String token = authHeader.substring(7);
        return authService.validateToken(token)
            .<ResponseEntity<?>>map(auth -> ResponseEntity.ok(auth.user()))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or expired token")));
    }

    /**
     * Logout - invalidate token.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * List available test users (only in dev mode).
     */
    @GetMapping("/test-users")
    public ResponseEntity<?> getTestUsers() {
        // Return list of test users for easy login during development
        return ResponseEntity.ok(Map.of(
            "users", java.util.List.of(
                Map.of(
                    "email", "admin@example.com",
                    "name", "Admin Adminsson",
                    "roles", java.util.List.of("ADMIN", "FLOW_EDITOR")
                ),
                Map.of(
                    "email", "handlaggare@example.com",
                    "name", "Hans Handlaggare",
                    "roles", java.util.List.of("MANAGER")
                ),
                Map.of(
                    "email", "medborgare@example.com",
                    "name", "Maria Medborgare",
                    "roles", java.util.List.of("USER")
                )
            )
        ));
    }
}
