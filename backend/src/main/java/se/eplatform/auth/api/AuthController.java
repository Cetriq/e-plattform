package se.eplatform.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.auth.dto.AuthResponse;
import se.eplatform.auth.dto.LoginRequest;
import se.eplatform.auth.service.MockAuthService;

import java.util.List;
import java.util.Map;

/**
 * Authentication controller for mock login.
 * Public endpoints - no authentication required.
 */
@RestController
@RequestMapping("/api/v1/public/auth")
@Tag(name = "Auth", description = "Autentisering och användarhantering. Alla endpoints är publika.")
public class AuthController {

    private final MockAuthService authService;

    public AuthController(MockAuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "Logga in",
        description = """
            Logga in med e-postadress och lösenord.

            **I utvecklingsmiljö** fungerar inloggning med valfritt lösenord för testanvändare.

            Vid lyckad inloggning returneras en JWT-token som ska användas i efterföljande anrop.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Inloggning lyckades",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                      "user": {
                        "id": "00000000-0000-0000-0000-000000000100",
                        "email": "admin@example.com",
                        "firstName": "Admin",
                        "lastName": "Adminsson",
                        "displayName": "Admin Adminsson",
                        "roles": ["ADMIN", "FLOW_EDITOR"],
                        "permissions": ["*"]
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Ogiltiga inloggningsuppgifter",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {"error": "Invalid credentials or user not found"}
                    """)
            )
        )
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Inloggningsuppgifter",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = LoginRequest.class),
                    examples = @ExampleObject(value = """
                        {
                          "email": "admin@example.com",
                          "password": "valfritt"
                        }
                        """)
                )
            )
            @Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password())
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials or user not found")));
    }

    @Operation(
        summary = "Hämta inloggad användare",
        description = "Validera token och hämta information om den inloggade användaren."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Användarinformation",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.UserInfo.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Ingen giltig token",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {"error": "No valid token provided"}
                    """)
            )
        )
    })
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            @Parameter(description = "JWT Bearer token", example = "Bearer eyJhbGciOiJIUzI1NiIs...")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
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

    @Operation(
        summary = "Logga ut",
        description = "Invalidera aktuell token och logga ut användaren."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Utloggning lyckades",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {"message": "Logged out successfully"}
                    """)
            )
        )
    })
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Parameter(description = "JWT Bearer token")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @Operation(
        summary = "Lista testanvändare",
        description = """
            Hämta lista över tillgängliga testanvändare.

            **OBS:** Endast tillgänglig i utvecklingsmiljö.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Lista med testanvändare",
        content = @Content(
            mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "users": [
                    {
                      "email": "admin@example.com",
                      "name": "Admin Adminsson",
                      "roles": ["ADMIN", "FLOW_EDITOR"]
                    },
                    {
                      "email": "handlaggare@example.com",
                      "name": "Hans Handlaggare",
                      "roles": ["MANAGER"]
                    },
                    {
                      "email": "medborgare@example.com",
                      "name": "Maria Medborgare",
                      "roles": ["USER"]
                    }
                  ]
                }
                """)
        )
    )
    @GetMapping("/test-users")
    public ResponseEntity<?> getTestUsers() {
        return ResponseEntity.ok(Map.of(
            "users", List.of(
                Map.of(
                    "email", "admin@example.com",
                    "name", "Admin Adminsson",
                    "roles", List.of("ADMIN", "FLOW_EDITOR")
                ),
                Map.of(
                    "email", "handlaggare@example.com",
                    "name", "Hans Handlaggare",
                    "roles", List.of("MANAGER")
                ),
                Map.of(
                    "email", "medborgare@example.com",
                    "name", "Maria Medborgare",
                    "roles", List.of("USER")
                )
            )
        ));
    }
}
