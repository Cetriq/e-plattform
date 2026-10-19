package se.eplatform.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.auth.dto.AuthResponse;
import se.eplatform.auth.dto.AuthResponse.OrganizationRef;
import se.eplatform.auth.dto.AuthResponse.UserInfo;
import se.eplatform.auth.dto.UpdateProfileRequest;
import se.eplatform.common.security.TokenService;
import se.eplatform.user.domain.OrganizationMembership;
import se.eplatform.user.domain.Role;
import se.eplatform.user.domain.User;
import se.eplatform.user.repository.RoleRepository;
import se.eplatform.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Demo authentication without e-legitimation.
 *
 * Visitors pick a persona instead of entering a password. The demo can be
 * gated with a shared access code, and citizens can be given a fresh,
 * isolated account per visit so they never see each other's cases.
 * In production this would be replaced with BankID or other e-legitimation.
 *
 * Staff work for one organisation at a time. The token names it (claim
 * "org"); a token naming an organisation the user is no longer a member of
 * is not accepted. Without the claim the user has only their global roles.
 */
@Service
public class MockAuthService {

    /** E-mail domain of the temporary citizen accounts (reserved, never delivered). */
    public static final String DEMO_EMAIL_DOMAIN = "@demo.example.com";

    private static final String CITIZEN_ROLE = "USER";

    /** Personas offered on the login page. */
    private static final List<String> PERSONA_EMAILS = List.of(
            "admin@example.com",
            "handlaggare@example.com",
            "informationssakerhet@example.com",
            "it-drift@example.com",
            "medborgare@example.com",
            "admin.grannkommun@example.com",
            "handlaggare.grannkommun@example.com",
            "informationssakerhet.grannkommun@example.com");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final byte[] accessCode;
    private final boolean isolatedCitizens;
    private final SecureRandom random = new SecureRandom();

    public MockAuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            TokenService tokenService,
            @Value("${eplatform.demo.access-code:}") String accessCode,
            @Value("${eplatform.demo.isolated-citizens:false}") boolean isolatedCitizens) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenService = tokenService;
        this.accessCode = accessCode.trim().getBytes(StandardCharsets.UTF_8);
        this.isolatedCitizens = isolatedCitizens;
    }

    public boolean isAccessCodeRequired() {
        return accessCode.length > 0;
    }

    public boolean isIsolatedCitizens() {
        return isolatedCitizens;
    }

    /**
     * True when no access code is configured, or when the given code matches.
     */
    public boolean isValidAccessCode(String code) {
        if (!isAccessCodeRequired()) {
            return true;
        }
        if (code == null) {
            return false;
        }
        // Constant-time comparison so the code can't be guessed from response times
        return MessageDigest.isEqual(accessCode, code.trim().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Log in as one of the personas. With isolated citizens enabled, the shared
     * citizen persona is not available; use {@link #createDemoCitizen()} instead.
     */
    @Transactional
    public Optional<AuthResponse> login(String email) {
        return userRepository.findByEmail(email)
            .filter(User::isActive)
            .filter(user -> !(isolatedCitizens && isCitizenOnly(user)))
            .map(user -> {
                user.setLastLoginAt(Instant.now());
                userRepository.save(user);
                return issue(user, defaultMembership(user));
            });
    }

    /**
     * Create a new citizen account for one demo visitor.
     */
    @Transactional
    public AuthResponse createDemoCitizen() {
        Role citizenRole = roleRepository.findByName(CITIZEN_ROLE)
            .orElseThrow(() -> new IllegalStateException("Role " + CITIZEN_ROLE + " is missing"));

        byte[] bytes = new byte[4];
        random.nextBytes(bytes);
        String suffix = HexFormat.of().formatHex(bytes);
        User user = new User("medborgare-" + suffix + DEMO_EMAIL_DOMAIN);
        user.setUsername("demo-" + suffix);
        user.setFirstName("Demo");
        user.setLastName("Medborgare " + suffix.substring(0, 4).toUpperCase());
        user.setEmailVerified(true);
        user.setLastLoginAt(Instant.now());
        user.addRole(citizenRole);

        User saved = userRepository.save(user);
        return issue(saved, null);
    }

    /**
     * Validate a token and return the current user.
     */
    @Transactional(readOnly = true)
    public Optional<AuthResponse> validateToken(String token) {
        return tokenService.verify(token).flatMap(claims -> activeUser(claims.userId())
            .flatMap(user -> sessionMembership(user, claims.organizationId())
                .map(membership -> new AuthResponse(token, toUserInfo(user, membership.orElse(null))))));
    }

    /**
     * Extend a session: a new token for the user owning a still valid one.
     */
    @Transactional(readOnly = true)
    public Optional<AuthResponse> refresh(String token) {
        return tokenService.verify(token).flatMap(claims -> activeUser(claims.userId())
            .flatMap(user -> sessionMembership(user, claims.organizationId())
                // Tokens from before organisations existed get the default one
                .map(membership -> issue(user, membership.orElseGet(() ->
                        claims.organizationId() == null ? defaultMembership(user) : null)))));
    }

    /**
     * Switch the organisation the user is working for. Empty when the token is
     * invalid or the user is not a member of the organisation.
     */
    @Transactional(readOnly = true)
    public Optional<AuthResponse> switchOrganization(String token, UUID organizationId) {
        return tokenService.verify(token).flatMap(claims -> activeUser(claims.userId())
            .flatMap(user -> membership(user, organizationId).map(membership -> issue(user, membership))));
    }

    /**
     * Update profile for the user owning this token. Email cannot be changed.
     */
    @Transactional
    public Optional<UserInfo> updateProfile(String token, UpdateProfileRequest request) {
        return tokenService.verify(token).flatMap(claims -> activeUser(claims.userId())
            .flatMap(user -> sessionMembership(user, claims.organizationId()).map(membership -> {
                boolean nameChanged = false;
                if (request.firstName() != null && !request.firstName().isBlank()) {
                    user.setFirstName(request.firstName().trim());
                    nameChanged = true;
                }
                if (request.lastName() != null && !request.lastName().isBlank()) {
                    user.setLastName(request.lastName().trim());
                    nameChanged = true;
                }
                if (nameChanged) {
                    // The display name would otherwise keep showing the old name
                    user.setDisplayName(null);
                }
                if (request.phone() != null) {
                    String trimmed = request.phone().trim();
                    user.setPhone(trimmed.isEmpty() ? null : trimmed);
                }
                User saved = userRepository.save(user);
                return toUserInfo(saved, membership.orElse(null));
            })));
    }

    /**
     * Personas that can be picked on the login page.
     */
    @Transactional(readOnly = true)
    public List<UserInfo> getPersonas() {
        return PERSONA_EMAILS.stream()
            .map(userRepository::findByEmail)
            .flatMap(Optional::stream)
            .filter(User::isActive)
            .filter(user -> !(isolatedCitizens && isCitizenOnly(user)))
            .map(user -> toUserInfo(user, defaultMembership(user)))
            .toList();
    }

    private boolean isCitizenOnly(User user) {
        return user.getMemberships().isEmpty()
            && user.getRoles().stream().allMatch(r -> CITIZEN_ROLE.equals(r.getName()));
    }

    private Optional<User> activeUser(UUID userId) {
        return userRepository.findById(userId).filter(User::isActive);
    }

    private AuthResponse issue(User user, OrganizationMembership membership) {
        UUID organizationId = membership == null ? null : membership.getOrganization().getId();
        return new AuthResponse(tokenService.issue(user.getId(), organizationId), toUserInfo(user, membership));
    }

    /**
     * The membership a token's organisation refers to: present and empty when the
     * token names no organisation, absent (token rejected) when the user is not a
     * member of the one it names.
     */
    private Optional<Optional<OrganizationMembership>> sessionMembership(User user, UUID organizationId) {
        if (organizationId == null) {
            return Optional.of(Optional.empty());
        }
        return membership(user, organizationId).map(Optional::of);
    }

    private Optional<OrganizationMembership> membership(User user, UUID organizationId) {
        return user.getMemberships().stream()
            .filter(m -> m.getOrganization().getId().equals(organizationId))
            .filter(m -> m.getOrganization().isActive())
            .findFirst();
    }

    /** The oldest membership, used when nothing else says which organisation to work for. */
    private OrganizationMembership defaultMembership(User user) {
        return user.getMemberships().stream()
            .filter(m -> m.getOrganization().isActive())
            .min(Comparator.comparing(OrganizationMembership::getCreatedAt))
            .orElse(null);
    }

    private UserInfo toUserInfo(User user, OrganizationMembership active) {
        List<Role> effective = Stream.concat(user.getRoles().stream(),
                active == null ? Stream.empty() : active.getRoles().stream()).toList();

        Set<String> roles = effective.stream()
            .map(Role::getName)
            .collect(Collectors.toSet());

        Set<String> permissions = effective.stream()
            .flatMap(r -> r.getPermissions().stream())
            .collect(Collectors.toSet());

        List<OrganizationRef> memberships = user.getMemberships().stream()
            .filter(m -> m.getOrganization().isActive())
            .map(m -> ref(m.getOrganization()))
            .sorted(Comparator.comparing(OrganizationRef::name))
            .toList();

        return new UserInfo(
            user.getId().toString(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getFullName(),
            user.getPhone(),
            roles,
            permissions,
            active == null ? null : ref(active.getOrganization()),
            memberships
        );
    }

    private static OrganizationRef ref(se.eplatform.user.domain.Organization organization) {
        return new OrganizationRef(organization.getId().toString(), organization.getName(), organization.getSlug());
    }
}
