package se.eplatform.auth.dto;

import java.util.List;
import java.util.Set;

public record AuthResponse(
    String token,
    UserInfo user
) {
    /**
     * @param roles               global roles plus the roles in the active organisation
     * @param activeOrganization  the organisation staff are working for, or null
     * @param memberships         organisations the user can switch to
     * @param clearance           highest confidentiality the user may see in the active organisation, or null
     */
    public record UserInfo(
        String id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String phone,
        Set<String> roles,
        Set<String> permissions,
        OrganizationRef activeOrganization,
        List<OrganizationRef> memberships,
        String clearance
    ) {}

    public record OrganizationRef(String id, String name, String slug) {}
}
