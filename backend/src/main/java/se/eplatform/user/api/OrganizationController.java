package se.eplatform.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.eplatform.auth.dto.AuthResponse.OrganizationRef;
import se.eplatform.user.repository.OrganizationRepository;

import java.util.List;

/**
 * Organisations offering e-services. Citizens pick one at /o/{slug}; the
 * choice decides the catalogue and who receives the case, not what the
 * citizen may see of their own cases.
 */
@RestController
@RequestMapping("/api/v1/public/organizations")
@Tag(name = "Organisationer", description = "Organisationer som erbjuder e-tjänster")
public class OrganizationController {

    private final OrganizationRepository organizations;

    public OrganizationController(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Operation(summary = "Lista organisationer", description = "Aktiva organisationer med namn och kortnamn (slug).")
    @GetMapping
    public List<OrganizationRef> list() {
        return organizations.findByActiveTrueOrderByName().stream()
                .filter(org -> !"PLATFORM".equals(org.getKind()))
                .map(org -> new OrganizationRef(org.getId().toString(), org.getName(), org.getSlug()))
                .toList();
    }
}
