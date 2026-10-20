package se.eplatform.flow.domain;

/**
 * Publication status of a flow.
 */
public enum FlowStatus {
    DRAFT,
    PUBLISHED,
    /** Replaced by a newer published version: takes no new cases, its existing cases continue. */
    SUPERSEDED,
    ARCHIVED
}
