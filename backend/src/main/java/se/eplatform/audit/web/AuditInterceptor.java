package se.eplatform.audit.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import se.eplatform.audit.domain.AuditOutcome;
import se.eplatform.audit.service.AuditService;

import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit entry for endpoints marked {@link Audited} once the
 * response status is known.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private final AuditService auditService;
    private final AuditSubjectResolver subjectResolver;

    public AuditInterceptor(AuditService auditService, AuditSubjectResolver subjectResolver) {
        this.auditService = auditService;
        this.subjectResolver = subjectResolver;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        Audited audited = method.getMethodAnnotation(Audited.class);
        if (audited == null) {
            return;
        }

        int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
        String entityId = (String) request.getAttribute(AuditContext.ENTITY_ID);
        if (entityId == null) {
            entityId = pathVariable(request, audited.idParam());
        }
        UUID subject = (UUID) request.getAttribute(AuditContext.SUBJECT);
        if (subject == null && !audited.entity().isEmpty() && entityId != null) {
            subject = subjectResolver.subjectOf(audited.entity(), entityId);
        }

        auditService.record(AuditService.Entry.of(audited.value())
                .entity(audited.entity().isEmpty() ? null : audited.entity(), entityId)
                .subject(subject)
                .details((String) request.getAttribute(AuditContext.DETAILS))
                .outcome(outcomeOf(status), status));
    }

    static AuditOutcome outcomeOf(int status) {
        if (status < 400) return AuditOutcome.SUCCESS;
        // Other people's data is reported as 404, so 404 counts as denied
        if (status == 401 || status == 403 || status == 404) return AuditOutcome.DENIED;
        return AuditOutcome.FAILURE;
    }

    @SuppressWarnings("unchecked")
    private static String pathVariable(HttpServletRequest request, String name) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map) {
            Object value = ((Map<String, String>) map).get(name);
            return value == null ? null : value.toString();
        }
        return null;
    }
}
