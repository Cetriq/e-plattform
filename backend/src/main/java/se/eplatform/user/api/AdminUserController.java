package se.eplatform.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.eplatform.auth.dto.AuthResponse;
import se.eplatform.user.api.dto.UserListDTO;
import se.eplatform.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Användare", description = "Administrativa endpoints för användarhantering. Kräver ADMIN-roll.")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(
        summary = "Lista användare",
        description = """
            Hämta paginerad lista över användare. Stöder fritextsökning på namn/e-post via `q`,
            filter på roll via `role`, samt paginering/sortering (`page`, `size`, `sort`).
            Endast läsbar i denna version.
            """
    )
    @GetMapping
    @Transactional(readOnly = true)
    public Page<UserListDTO> listUsers(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "role", required = false) String role,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        requireAdmin();

        String queryFilter = query == null ? "" : query.trim();
        String roleFilter = role == null ? "" : role.trim();

        return userRepository.searchWithRole(queryFilter, roleFilter, pageable)
            .map(UserListDTO::from);
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthResponse.UserInfo principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inloggning krävs");
        }
        boolean isAdmin = principal.roles().contains("ADMIN") || principal.roles().contains("FLOW_EDITOR");
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kräver ADMIN-behörighet");
        }
    }
}
