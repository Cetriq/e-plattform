package se.eplatform.audit.service;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.domain.AuditEvent;
import se.eplatform.audit.domain.AuditOutcome;
import se.eplatform.audit.repository.AuditEventRepository;
import se.eplatform.auth.dto.AuthResponse.UserInfo;
import se.eplatform.common.security.ClientIp;
import se.eplatform.common.security.CurrentUser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Writes the traceability log (spårbarhetslogg).
 *
 * Entries are written synchronously in their own transaction, so the acting
 * user and request are always known and the entry is kept even when the
 * request itself fails or is denied. Each entry stores the SHA-256 of the
 * previous entry and of its own content, forming a chain that reveals any
 * later change (see {@link AuditChainVerifier}).
 *
 * Every organisation has its own chain, and so does the platform. An entry
 * belongs to the organisation of the thing it is about (a case, file, e-service,
 * category or organisation), otherwise to the organisation the actor works
 * for, otherwise to the platform.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    static final String GENESIS = "0".repeat(64);
    /** Chain of events that belong to no organisation. */
    public static final String PLATFORM_CHAIN = "platform";
    /** The single chain of entries written before chains per organisation (hash version 1). */
    public static final String LEGACY_CHAIN = "global";

    private final AuditEventRepository repository;
    private final EntityManager entityManager;
    private final ClientIp clientIp;

    public AuditService(AuditEventRepository repository, EntityManager entityManager, ClientIp clientIp) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clientIp = clientIp;
    }

    /**
     * What happened, to what, and whose personal data it concerned.
     */
    public record Entry(
            AuditAction action,
            AuditOutcome outcome,
            String entityType,
            String entityId,
            UUID subjectUserId,
            String details,
            Integer responseStatus,
            UUID organizationId) {

        public static Entry of(AuditAction action) {
            return new Entry(action, AuditOutcome.SUCCESS, null, null, null, null, null, null);
        }

        public Entry entity(String type, Object id) {
            return new Entry(action, outcome, type, id == null ? null : id.toString(), subjectUserId, details, responseStatus, organizationId);
        }

        public Entry subject(UUID subject) {
            return new Entry(action, outcome, entityType, entityId, subject, details, responseStatus, organizationId);
        }

        public Entry details(String text) {
            return new Entry(action, outcome, entityType, entityId, subjectUserId, text, responseStatus, organizationId);
        }

        public Entry outcome(AuditOutcome result, Integer status) {
            return new Entry(action, result, entityType, entityId, subjectUserId, details, status, organizationId);
        }

        /** Put the entry in this organisation's chain regardless of entity and actor. */
        public Entry organization(UUID organization) {
            return new Entry(action, outcome, entityType, entityId, subjectUserId, details, responseStatus, organization);
        }
    }

    /**
     * Record an event for the current user and request. Never throws: a
     * failure is logged, but must not break the request it describes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Entry entry) {
        try {
            AuditEvent event = new AuditEvent(entry.action(), entry.outcome());
            event.setEntityType(entry.entityType());
            event.setEntityId(entry.entityId());
            event.setSubjectUserId(entry.subjectUserId());
            event.setDetails(truncate(entry.details(), 2000));
            event.setResponseStatus(entry.responseStatus());
            applyActor(event);
            applyRequest(event);
            append(event, entry, CurrentUser.activeOrganizationId().orElse(null));
        } catch (Exception e) {
            log.error("Could not write audit entry {} {}", entry.action(), entry.entityId(), e);
        }
    }

    /**
     * Record an event where the actor is known but not (yet) logged in, e.g.
     * a successful login or a new demo account.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFor(UserInfo actor, Entry entry) {
        try {
            AuditEvent event = new AuditEvent(entry.action(), entry.outcome());
            event.setEntityType(entry.entityType());
            event.setEntityId(entry.entityId());
            event.setSubjectUserId(entry.subjectUserId());
            event.setDetails(truncate(entry.details(), 2000));
            event.setResponseStatus(entry.responseStatus());
            event.setUserId(actor.id());
            event.setUserEmail(actor.email());
            event.setUserName(actor.displayName());
            applyRequest(event);
            append(event, entry, actor.activeOrganization() == null ? null
                    : UUID.fromString(actor.activeOrganization().id()));
        } catch (Exception e) {
            log.error("Could not write audit entry {} {}", entry.action(), entry.entityId(), e);
        }
    }

    /**
     * Record an event done by the system itself (scheduled jobs).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(Entry entry) {
        try {
            AuditEvent event = new AuditEvent(entry.action(), entry.outcome());
            event.setEntityType(entry.entityType());
            event.setEntityId(entry.entityId());
            event.setSubjectUserId(entry.subjectUserId());
            event.setDetails(truncate(entry.details(), 2000));
            event.setUserId("system");
            event.setUserName("System");
            append(event, entry, null);
        } catch (Exception e) {
            log.error("Could not write audit entry {} {}", entry.action(), entry.entityId(), e);
        }
    }

    private void append(AuditEvent event, Entry entry, UUID actorOrganization) {
        UUID organization = entry.organizationId() != null ? entry.organizationId()
                : entityOrganization(entry.entityType(), entry.entityId()).orElse(actorOrganization);
        if (se.eplatform.user.domain.Organization.PLATFORM_ID.equals(organization)) {
            organization = null; // the platform's own events form the platform chain
        }
        String chain = organization == null ? PLATFORM_CHAIN : organization.toString();
        event.setOrganizationId(organization);
        event.setChainKey(chain);

        // Serialize writers to this chain (across instances) so each entry links
        // to the latest one. The retention procedures take the same lock.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", "audit:" + chain)
                .getSingleResult();
        String prev = repository.findLatestHash(chain).orElse(GENESIS);
        event.setPrevHash(prev);
        event.setHash(hash(prev, event));
        repository.save(event);
    }

    /** The organisation owning the thing an entry is about, if it is one that has one. */
    private java.util.Optional<UUID> entityOrganization(String type, String id) {
        if (type == null || id == null) return java.util.Optional.empty();
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
        // A database function, because RLS hides another organisation's case from
        // the person trying to reach it, and that attempt belongs in its log
        Object organization = entityManager
                .createNativeQuery("SELECT audit_entity_organization(:type, :id)")
                .setParameter("type", type)
                .setParameter("id", uuid)
                .getSingleResult();
        return java.util.Optional.ofNullable((UUID) organization);
    }

    static String hash(String prevHash, AuditEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prevHash.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(event.canonicalContent().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void applyActor(AuditEvent event) {
        CurrentUser.get().ifPresentOrElse(user -> {
            event.setUserId(user.id());
            event.setUserEmail(user.email());
            event.setUserName(user.displayName());
        }, () -> event.setUserId("anonymous"));
    }

    private void applyRequest(AuditEvent event) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            event.setIpAddress(clientIp.of(request));
            event.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            event.setRequestMethod(request.getMethod());
            event.setRequestPath(truncate(request.getRequestURI(), 500));
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
