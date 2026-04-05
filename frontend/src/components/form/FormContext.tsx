'use client';

import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';
import type { FormValues, FieldStates, FieldState, QueryState, FormContext as FormContextType, Evaluator, Step } from './types';
import { useEvaluator, buildEvaluatorMaps, type ComputedFieldStates } from './useEvaluator';

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
}

export function FormProvider({
  children,
  steps = [],
  initialValues = {},
  initialFieldStates = {},
  onChange,
  userId,
  caseId,
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

  const setValue = useCallback((queryId: string, value: unknown) => {
    setValues((prev) => {
      const next = { ...prev, [queryId]: value };
      onChange?.(next);
      return next;
    });
  }, [onChange]);

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

  const setFieldErrors = useCallback((queryId: string, errors: string[]) => {
    setFieldStates((prev) => ({
      ...prev,
      [queryId]: {
        ...(prev[queryId] || defaultFieldState),
        errors,
      },
    }));
  }, []);

  const getFieldState = useCallback((queryId: string): FieldState => {
    const manualState = fieldStates[queryId];
    const computedState = computedStates[queryId];

    // Computed state from evaluators takes precedence for visibility
    const state = computedState || manualState?.state || defaultFieldState.state;

    return {
      state,
      errors: manualState?.errors || [],
      touched: manualState?.touched || false,
    };
  }, [fieldStates, computedStates]);

  const contextValue = useMemo<FormContextType>(() => ({
    values,
    fieldStates,
    setValue,
    setTouched,
    getFieldState,
    userId,
    caseId,
  }), [values, fieldStates, setValue, setTouched, getFieldState, userId, caseId]);

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
