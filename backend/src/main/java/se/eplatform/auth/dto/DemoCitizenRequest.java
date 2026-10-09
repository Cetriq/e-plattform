package se.eplatform.auth.dto;

/**
 * Request for a new, isolated demo citizen account.
 */
public record DemoCitizenRequest(
    /** Shared demo access code; required when the demo is gated. */
    String accessCode
) {}
