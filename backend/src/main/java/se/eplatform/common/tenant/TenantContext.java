package se.eplatform.common.tenant;

import se.eplatform.common.security.CurrentUser;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The organisation and user the database's row level security works for.
 *
 * In a request they come from the verified token (CurrentUser), never from
 * request parameters or bodies. Background jobs, which have no user, name
 * the organisation they work for with {@link #forOrganization}. With neither,
 * the database shows no case data at all.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> JOB_ORGANIZATION = new ThreadLocal<>();

    private TenantContext() {}

    /** Run work for one organisation outside a request (jobs). */
    public static <T> T forOrganization(UUID organizationId, Supplier<T> work) {
        UUID previous = JOB_ORGANIZATION.get();
        JOB_ORGANIZATION.set(organizationId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                JOB_ORGANIZATION.remove();
            } else {
                JOB_ORGANIZATION.set(previous);
            }
        }
    }

    static Optional<UUID> organization() {
        UUID job = JOB_ORGANIZATION.get();
        return job != null ? Optional.of(job) : CurrentUser.activeOrganizationId();
    }

    static Optional<UUID> user() {
        return CurrentUser.get().map(user -> UUID.fromString(user.id()));
    }
}
