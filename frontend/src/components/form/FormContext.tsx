'use client';

import React, { createContext, useContext, useState, useCallback, useMemo, useEffect, useRef } from 'react';
import type { FormValues, FieldStates, FieldState, QueryState, QueryDefinition, FormContext as FormContextType, Step } from './types';
import { useEvaluator, buildEvaluatorMaps } from './useEvaluator';
import { validateQuery } from './validation';

const defaultFieldState: FieldState = {
  state: 'VISIBLE',
  errors: [],
  touched: false,
};

const FormContext = createContext<FormContextType | null>(null);

interface FormProviderProps {
  children: React.ReactNode;
  steps?: Step[];
  initialValues?: FormValues;
  initialFieldStates?: FieldStates;
  onChange?: (values: FormValues) => void;
  userId?: string;
  caseId?: string;
  ensureCaseId?: () => Promise<string>;
}

export function FormProvider({
  children,
  steps = [],
  initialValues = {},
  initialFieldStates = {},
  onChange,
  userId,
  caseId,
  ensureCaseId,
}: FormProviderProps) {
  const [values, setValues] = useState<FormValues>(initialValues);
  const [fieldStates, setFieldStates] = useState<FieldStates>(initialFieldStates);

  // Build evaluator maps from steps
  const { evaluatorsBySourceQuery, defaultStates } = useMemo(
    () => buildEvaluatorMaps(steps),
    [steps]
  );

  // Compute states based on evaluators
  const computedStates = useEvaluator(evaluatorsBySourceQuery, values, defaultStates);

  const queriesById = useMemo(() => {
    const map: Record<string, QueryDefinition> = {};
    steps.forEach((step) => step.queries.forEach((q) => { map[q.id] = q; }));
    return map;
  }, [steps]);

  const setValue = useCallback((queryId: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [queryId]: value }));
  }, []);

  // Notify the parent outside of the state updater so it runs once per change
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const isFirstRenderRef = useRef(true);
  useEffect(() => {
    if (isFirstRenderRef.current) {
      isFirstRenderRef.current = false;
      return;
    }
    onChangeRef.current?.(values);
  }, [values]);

  const setTouched = useCallback((queryId: string) => {
    setFieldStates((prev) => ({
      ...prev,
      [queryId]: {
        ...(prev[queryId] || defaultFieldState),
        touched: true,
      },
    }));
  }, []);

  const setFieldState = useCallback((queryId: string, state: QueryState) => {
    setFieldStates((prev) => ({
      ...prev,
      [queryId]: {
        ...(prev[queryId] || defaultFieldState),
        state,
      },
    }));
  }, []);

  const resolveState = useCallback((queryId: string): QueryState => {
    // Computed state from evaluators takes precedence for visibility
    return computedStates[queryId] || fieldStates[queryId]?.state || defaultFieldState.state;
  }, [fieldStates, computedStates]);

  const errorsFor = useCallback((queryId: string, state: QueryState): string[] => {
    const query = queriesById[queryId];
    if (!query || state === 'HIDDEN' || state === 'DISABLED' || state === 'READONLY') return [];
    return validateQuery(query, values[queryId], query.required || state === 'VISIBLE_REQUIRED');
  }, [queriesById, values]);

  const getFieldState = useCallback((queryId: string): FieldState => {
    const state = resolveState(queryId);
    const touched = fieldStates[queryId]?.touched || false;

    return {
      state,
      errors: touched ? errorsFor(queryId, state) : [],
      touched,
    };
  }, [fieldStates, resolveState, errorsFor]);

  const validateQueries = useCallback((queryIds: string[]): string[] => {
    // Hidden fields are skipped so they don't show errors as soon as they appear
    const visibleIds = queryIds.filter((id) => resolveState(id) !== 'HIDDEN');
    setFieldStates((prev) => {
      const next = { ...prev };
      visibleIds.forEach((id) => {
        next[id] = { ...(prev[id] || defaultFieldState), touched: true };
      });
      return next;
    });
    return visibleIds.filter((id) => errorsFor(id, resolveState(id)).length > 0);
  }, [errorsFor, resolveState]);

  const contextValue = useMemo<FormContextType>(() => ({
    values,
    fieldStates,
    setValue,
    setTouched,
    getFieldState,
    validateQueries,
    userId,
    caseId,
    ensureCaseId,
  }), [values, fieldStates, setValue, setTouched, getFieldState, validateQueries, userId, caseId, ensureCaseId]);

  return (
    <FormContext.Provider value={contextValue}>
      {children}
    </FormContext.Provider>
  );
}

export function useFormContext(): FormContextType {
  const context = useContext(FormContext);
  if (!context) {
    throw new Error('useFormContext must be used within a FormProvider');
  }
  return context;
}

export function useField(queryId: string) {
  const { values, setValue, setTouched, getFieldState } = useFormContext();

  const value = values[queryId];
  const fieldState = getFieldState(queryId);

  const handleChange = useCallback((newValue: unknown) => {
    setValue(queryId, newValue);
  }, [queryId, setValue]);

  const handleBlur = useCallback(() => {
    setTouched(queryId);
  }, [queryId, setTouched]);

  return {
    value,
    onChange: handleChange,
    onBlur: handleBlur,
    ...fieldState,
  };
}
