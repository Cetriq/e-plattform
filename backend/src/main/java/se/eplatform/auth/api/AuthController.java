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
import se.eplatform.auth.dto.DemoCitizenRequest;
import se.eplatform.auth.dto.LoginRequest;
import se.eplatform.auth.dto.UpdateProfileRequest;
import se.eplatform.auth.service.MockAuthService;

import java.util.List;
import java.util.Map;

/**
 * Authentication controller for the demo login.
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
        summary = "Logga in som demopersona",
        description = """
            Logga in som en av demopersonorna (se `GET /test-users`). Inget lösenord krävs.

            Om demon är skyddad med åtkomstkod måste `accessCode` anges.

            Vid lyckad inloggning returneras en signerad JWT som ska användas i efterföljande anrop.
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
            description = "Fel åtkomstkod eller okänd persona",
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
                          "accessCode": "demo-kod"
                        }
                        """)
                )
            )
            @Valid @RequestBody LoginRequest request) {
        if (!authService.isValidAccessCode(request.accessCode())) {
            return invalidAccessCode();
        }
        return authService.login(request.email())
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials or user not found")));
    }

    @Operation(
        summary = "Skapa demomedborgare",
        description = """
            Skapar ett nytt, eget medborgarkonto för en demobesökare och loggar in det.
            Besökaren ser bara sina egna ärenden.

            Om demon är skyddad med åtkomstkod måste `accessCode` anges.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kontot skapades",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Fel åtkomstkod")
    })
    @PostMapping("/demo-citizen")
    public ResponseEntity<?> createDemoCitizen(@RequestBody(required = false) DemoCitizenRequest request) {
        if (!authService.isValidAccessCode(request == null ? null : request.accessCode())) {
            return invalidAccessCode();
        }
        return ResponseEntity.ok(authService.createDemoCitizen());
    }

    @Operation(
        summary = "Inloggningsinställningar",
        description = "Talar om för inloggningssidan om åtkomstkod krävs och om medborgare får egna demokonton."
    )
    @GetMapping("/config")
    public LoginConfig getLoginConfig() {
        return new LoginConfig(authService.isAccessCodeRequired(), authService.isIsolatedCitizens());
    }

    public record LoginConfig(boolean accessCodeRequired, boolean isolatedCitizens) {}

    private ResponseEntity<?> invalidAccessCode() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Fel åtkomstkod"));
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
        summary = "Uppdatera egen profil",
        description = """
            Uppdatera förnamn, efternamn och/eller telefonnummer för inloggad användare.

            E-post kan inte ändras eftersom den i produktion hämtas från e-legitimation.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Profil uppdaterad",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.UserInfo.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "Ogiltig token")
    })
    @PatchMapping("/me")
    public ResponseEntity<?> updateProfile(
            @Parameter(description = "JWT Bearer token")
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "No valid token provided"));
        }

        String token = authHeader.substring(7);
        return authService.updateProfile(token, request)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or expired token")));
    }

    @Operation(
        summary = "Logga ut",
        description = """
            Tokens är tillståndslösa och går ut av sig själva, så klienten loggar ut
            genom att kasta sin token. Endpointen finns kvar för kompatibilitet.
            """
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
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @Operation(
        summary = "Lista demopersonor",
        description = """
            Personor som kan väljas på inloggningssidan. När medborgare får egna
            demokonton visas inte den delade medborgarpersonan.
            """
    )
    @ApiResponse(responseCode = "200", description = "Lista med personor")
    @GetMapping("/test-users")
    public Map<String, List<Persona>> getTestUsers() {
        List<Persona> personas = authService.getPersonas().stream()
            .map(u -> new Persona(u.email(), u.displayName(), List.copyOf(u.roles())))
            .toList();
        return Map.of("users", personas);
    }

    public record Persona(String email, String name, List<String> roles) {}
}
