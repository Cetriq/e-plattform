package se.eplatform.flow.domain;

/**
 * Types of form fields/queries.
 */
public enum QueryType {
    // Text inputs
    TEXT,
    TEXTAREA,
    NUMBER,
    EMAIL,
    PHONE,
    URL,

    // Date/Time
    DATE,
    DATETIME,
    TIME,

    // Selection
    SELECT,
    MULTISELECT,
    RADIO,
    CHECKBOX,

    // Files
    FILE,
    IMAGE,

    // Special
    MAP,
    LOCATION,
    SIGNATURE,
    ORGANIZATION,
    PERSON,

    // Layout elements (non-input)
    HEADING,
    PARAGRAPH,
    DIVIDER
}
