package se.eplatform.audit.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Lets an endpoint add facts to its audit entry that the path doesn't carry,
 * such as the id of a newly created object or a short description.
 */
public final class AuditContext {

    static final String ENTITY_ID = AuditContext.class.getName() + ".entityId";
    static final String SUBJECT = AuditContext.class.getName() + ".subject";
    static final String DETAILS = AuditContext.class.getName() + ".details";

    private AuditContext() {}

    public static void entityId(Object id) {
        set(ENTITY_ID, id == null ? null : id.toString());
    }

    public static void subject(UUID userId) {
        set(SUBJECT, userId);
    }

    /** A short description; must not contain answers or other personal data. */
    public static void details(String text) {
        set(DETAILS, text);
    }

    private static void set(String key, Object value) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            request.setAttribute(key, value);
        }
    }
}
