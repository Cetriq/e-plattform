package se.eplatform.flow.domain;

/**
 * Visibility/requirement state of a query field.
 */
public enum QueryState {
    /**
     * Field is visible but not required.
     */
    VISIBLE,

    /**
     * Field is visible and required.
     */
    VISIBLE_REQUIRED,

    /**
     * Field is hidden from the user.
     */
    HIDDEN
}
