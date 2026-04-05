package se.eplatform.flow.domain;

/**
 * Types of conditional evaluators for dynamic form behavior.
 */
public enum EvaluatorType {
    VALUE_EQUALS,
    VALUE_NOT_EQUALS,
    VALUE_IN,
    VALUE_NOT_IN,
    VALUE_CONTAINS,
    VALUE_NOT_CONTAINS,
    VALUE_GREATER_THAN,
    VALUE_LESS_THAN,
    VALUE_BETWEEN,
    REGEX_MATCH,
    IS_EMPTY,
    IS_NOT_EMPTY,
    CUSTOM
}
