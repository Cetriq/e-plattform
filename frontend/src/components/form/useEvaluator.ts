'use client';

import { useMemo } from 'react';
import type { Evaluator, EvaluatorType, FormValues, QueryState } from './types';

/**
 * Evaluate a single condition against a value.
 */
function evaluateCondition(
  evaluatorType: EvaluatorType,
  condition: Record<string, unknown>,
  value: unknown
): boolean {
  switch (evaluatorType) {
    case 'VALUE_EQUALS': {
      const expected = condition.value;
      return expected !== undefined && expected === value;
    }
    case 'VALUE_NOT_EQUALS': {
      const expected = condition.value;
      return expected === undefined || expected !== value;
    }
    case 'VALUE_IN': {
      const values = condition.values as unknown[] | undefined;
      return Array.isArray(values) && values.includes(value);
    }
    case 'VALUE_NOT_IN': {
      const values = condition.values as unknown[] | undefined;
      return !Array.isArray(values) || !values.includes(value);
    }
    case 'VALUE_CONTAINS': {
      const searchValue = condition.value as string | undefined;
      return (
        typeof value === 'string' &&
        typeof searchValue === 'string' &&
        value.includes(searchValue)
      );
    }
    case 'VALUE_NOT_CONTAINS': {
      const searchValue = condition.value as string | undefined;
      return (
        typeof value !== 'string' ||
        typeof searchValue !== 'string' ||
        !value.includes(searchValue)
      );
    }
    case 'VALUE_GREATER_THAN': {
      const threshold = condition.value as number | undefined;
      return (
        typeof value === 'number' &&
        typeof threshold === 'number' &&
        value > threshold
      );
    }
    case 'VALUE_LESS_THAN': {
      const threshold = condition.value as number | undefined;
      return (
        typeof value === 'number' &&
        typeof threshold === 'number' &&
        value < threshold
      );
    }
    case 'VALUE_BETWEEN': {
      const min = condition.min as number | undefined;
      const max = condition.max as number | undefined;
      return (
        typeof value === 'number' &&
        typeof min === 'number' &&
        typeof max === 'number' &&
        value >= min &&
        value <= max
      );
    }
    case 'REGEX_MATCH': {
      const pattern = condition.pattern as string | undefined;
      if (typeof value !== 'string' || typeof pattern !== 'string') {
        return false;
      }
      try {
        return new RegExp(pattern).test(value);
      } catch {
        return false;
      }
    }
    case 'IS_EMPTY': {
      if (value === null || value === undefined) return true;
      if (typeof value === 'string') return value.trim() === '';
      if (Array.isArray(value)) return value.length === 0;
      return false;
    }
    case 'IS_NOT_EMPTY': {
      if (value === null || value === undefined) return false;
      if (typeof value === 'string') return value.trim() !== '';
      if (Array.isArray(value)) return value.length > 0;
      return true;
    }
    case 'CUSTOM':
    default:
      return false;
  }
}

/**
 * Interface for computed field states based on evaluators.
 */
export interface ComputedFieldStates {
  [queryId: string]: QueryState;
}

/**
 * Hook to compute field states based on evaluators and current form values.
 *
 * @param evaluatorsBySourceQuery - Map of source query ID to its evaluators
 * @param values - Current form values
 * @param defaultStates - Default states for each query
 * @returns Computed states for each query affected by evaluators
 */
export function useEvaluator(
  evaluatorsBySourceQuery: Map<string, Evaluator[]>,
  values: FormValues,
  defaultStates: Map<string, QueryState>
): ComputedFieldStates {
  return useMemo(() => {
    const computedStates: ComputedFieldStates = {};

    // Initialize with default states
    defaultStates.forEach((state, queryId) => {
      computedStates[queryId] = state;
    });

    // Evaluate each source query's evaluators
    evaluatorsBySourceQuery.forEach((evaluators, sourceQueryId) => {
      const sourceValue = values[sourceQueryId];

      for (const evaluator of evaluators) {
        const conditionMet = evaluateCondition(
          evaluator.evaluatorType,
          evaluator.condition,
          sourceValue
        );

        if (conditionMet) {
          // Apply target state to all target queries
          for (const targetId of evaluator.targetQueryIds) {
            computedStates[targetId] = evaluator.targetState;
          }
        }
      }
    });

    return computedStates;
  }, [evaluatorsBySourceQuery, values, defaultStates]);
}

/**
 * Build evaluator maps from flow steps.
 */
export function buildEvaluatorMaps(steps: { queries: { id: string; defaultState: QueryState; evaluators: Evaluator[] }[] }[]) {
  const evaluatorsBySourceQuery = new Map<string, Evaluator[]>();
  const defaultStates = new Map<string, QueryState>();

  for (const step of steps) {
    for (const query of step.queries) {
      defaultStates.set(query.id, query.defaultState);

      if (query.evaluators && query.evaluators.length > 0) {
        evaluatorsBySourceQuery.set(query.id, query.evaluators);
      }
    }
  }

  return { evaluatorsBySourceQuery, defaultStates };
}
