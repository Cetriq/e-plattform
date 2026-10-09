package se.eplatform.audit.web;

import se.eplatform.audit.domain.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records a traceability log entry for every call of the annotated endpoint,
 * including denied and failed calls, with the outcome taken from the response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditAction value();

    /** Kind of object, e.g. "CASE", "FILE", "USER", "FLOW". */
    String entity() default "";

    /** Path variable holding the object's id. */
    String idParam() default "id";
}
