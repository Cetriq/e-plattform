package se.eplatform.user.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A staff member's place in an organisation, with the roles they have there.
 * Staff work for one organisation at a time (the active organisation in their
 * token); roles from other memberships do not apply meanwhile.
 */
@Entity
@Table(name = "organization_members")
public class OrganizationMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /** Highest confidentiality level of cases the member may see: NORMAL, RESTRICTED or CONFIDENTIAL. */
    @Column(nullable = false, length = 20)
    private String clearance = "NORMAL";

    @ManyToMany
    @JoinTable(
            name = "organization_member_roles",
            joinColumns = @JoinColumn(name = "member_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OrganizationMembership() {}

    public OrganizationMembership(User user, Organization organization) {
        this.user = user;
        this.organization = organization;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getClearance() {
        return clearance;
    }

    public void setClearance(String clearance) {
        this.clearance = clearance;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
