'use client';

import React, { useState, useCallback, useEffect, useRef } from 'react';
import type { Flow, Step, FormValues } from './types';
import { FormProvider, useFormContext } from './FormContext';
import { StepIndicator } from './StepIndicator';
import { QueryRenderer } from './QueryRenderer';
import { AutoSaveIndicator, type AutoSaveStatus } from './AutoSaveIndicator';
import { toast } from '@/hooks/useToast';

interface FormRendererProps {
  flow: Flow;
  initialValues?: FormValues;
  onSubmit: (values: FormValues) => void;
  onSaveDraft?: (values: FormValues) => void;
  onCancel?: () => void;
  userId?: string;
  caseId?: string;
  /** Creates the draft case if needed, so files can be attached to it. */
  ensureCaseId?: () => Promise<string>;
}

export function FormRenderer({
  flow,
  initialValues = {},
  onSubmit,
  onSaveDraft,
  onCancel,
  userId,
  caseId,
  ensureCaseId,
}: FormRendererProps) {
  const [values, setValues] = useState<FormValues>(initialValues);
  const [autoSaveStatus, setAutoSaveStatus] = useState<AutoSaveStatus>('idle');
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);

  const steps = flow.steps;

  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const savedSignatureRef = useRef<string>(JSON.stringify(initialValues));
  const pendingSaveRef = useRef<FormValues | null>(null);
  const savedToStableRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const runSave = useCallback(
    async (valuesToSave: FormValues): Promise<boolean> => {
      if (!onSaveDraft) return false;
      const signature = JSON.stringify(valuesToSave);
      if (signature === savedSignatureRef.current) return true;

      setAutoSaveStatus('saving');
      try {
        await onSaveDraft(valuesToSave);
        savedSignatureRef.current = signature;
        setLastSavedAt(new Date());
        setAutoSaveStatus('saved');
        if (savedToStableRef.current) clearTimeout(savedToStableRef.current);
        savedToStableRef.current = setTimeout(() => {
          setAutoSaveStatus((prev) => (prev === 'saved' ? 'idle' : prev));
        }, 4000);
        return true;
      } catch {
        setAutoSaveStatus('error');
        return false;
      }
    },
    [onSaveDraft]
  );

  const handleValuesChange = useCallback(
    (newValues: FormValues) => {
      setValues(newValues);
      if (!onSaveDraft) return;

      pendingSaveRef.current = newValues;
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = setTimeout(() => {
        if (pendingSaveRef.current) {
          runSave(pendingSaveRef.current);
          pendingSaveRef.current = null;
        }
      }, 1500);
    },
    [onSaveDraft, runSave]
  );

  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
      if (savedToStableRef.current) clearTimeout(savedToStableRef.current);
    };
  }, []);

  const retryAutoSave = useCallback(() => {
    if (pendingSaveRef.current) {
      runSave(pendingSaveRef.current);
    } else {
      runSave(values);
    }
  }, [runSave, values]);

  const handleSaveDraft = async () => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    pendingSaveRef.current = null;
    const ok = await runSave(values);
    if (ok) {
      toast.success('Utkast sparat', 'Dina uppgifter har sparats som utkast.');
    } else {
      toast.error('Kunde inte spara utkast', 'Försök igen om en stund.');
    }
  };

  const handleSubmit = (submittedValues: FormValues) => {
    // A pending autosave must not run after the case has been submitted
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    pendingSaveRef.current = null;
    return onSubmit(submittedValues);
  };

  return (
    <FormProvider steps={steps} initialValues={values} onChange={handleValuesChange} userId={userId} caseId={caseId} ensureCaseId={ensureCaseId}>
      <FormBody
        steps={steps}
        onSubmit={handleSubmit}
        onCancel={onCancel}
        onSaveDraft={onSaveDraft ? handleSaveDraft : undefined}
        autoSave={
          onSaveDraft ? (
            <AutoSaveIndicator
              status={autoSaveStatus}
              lastSavedAt={lastSavedAt}
              onRetry={retryAutoSave}
            />
          ) : null
        }
      />
    </FormProvider>
  );
}

interface FormBodyProps {
  steps: Step[];
  onSubmit: (values: FormValues) => void;
  onCancel?: () => void;
  onSaveDraft?: () => void;
  autoSave: React.ReactNode;
}

function FormBody({ steps, onSubmit, onCancel, onSaveDraft, autoSave }: FormBodyProps) {
  const { values, validateQueries, getFieldState } = useFormContext();
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showErrorSummary, setShowErrorSummary] = useState(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const hasNavigatedRef = useRef(false);

  const currentStep = steps[currentStepIndex];
  const isFirstStep = currentStepIndex === 0;
  const isLastStep = currentStepIndex === steps.length - 1;

  // Count is derived so the summary updates while the user corrects the fields
  const invalidCount = showErrorSummary
    ? currentStep.queries.filter((q) => getFieldState(q.id).errors.length > 0).length
    : 0;

  // Move focus to the step heading so screen reader users know the step changed
  useEffect(() => {
    if (!hasNavigatedRef.current) return;
    headingRef.current?.focus();
  }, [currentStepIndex]);

  const goToStep = (index: number) => {
    hasNavigatedRef.current = true;
    setCurrentStepIndex(index);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const validateStep = (step: Step): boolean => {
    const invalid = validateQueries(step.queries.map((q) => q.id));
    setShowErrorSummary(invalid.length > 0);
    if (invalid.length > 0 && step === currentStep) {
      // Focus the first invalid field after the error messages have rendered
      // (radio groups have no element with the field id, so fall back to the input name)
      requestAnimationFrame(() => {
        const target =
          document.getElementById(invalid[0]) ??
          document.querySelector<HTMLElement>(`[name="${CSS.escape(invalid[0])}"]`);
        target?.focus();
      });
    }
    return invalid.length === 0;
  };

  const handlePrevious = () => {
    if (!isFirstStep) {
      setShowErrorSummary(false);
      goToStep(currentStepIndex - 1);
    }
  };

  const handleNext = () => {
    if (!validateStep(currentStep)) return;
    if (!isLastStep) {
      goToStep(currentStepIndex + 1);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // Validate every step; jump to the first one that has errors
    for (let i = 0; i < steps.length; i++) {
      if (!validateStep(steps[i])) {
        if (i !== currentStepIndex) goToStep(i);
        return;
      }
    }
    setIsSubmitting(true);
    try {
      await onSubmit(values);
      toast.success('Ansökan skickad', 'Din ansökan har skickats in.');
    } catch {
      // The page shows details about the failure; rethrowing here would only
      // surface as an unhandled rejection from the event handler
      toast.error(
        'Något gick fel',
        'Kunde inte skicka in ansökan. Försök igen senare.'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleStepClick = (index: number) => {
    // Going back is always allowed; going forward requires the steps in between to be valid
    if (index < currentStepIndex) setShowErrorSummary(false);
    for (let i = currentStepIndex; i < index; i++) {
      if (!validateStep(steps[i])) {
        if (i !== currentStepIndex) goToStep(i);
        return;
      }
    }
    goToStep(index);
  };

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-6">
      {autoSave && <div className="flex justify-end">{autoSave}</div>}

      {/* Step indicator */}
      {steps.length > 1 && (
        <StepIndicator
          steps={steps}
          currentStepIndex={currentStepIndex}
          onStepClick={handleStepClick}
          allowNavigation={true}
        />
      )}

      {invalidCount > 0 && (
        <div
          role="alert"
          className="p-4 bg-red-50 border border-red-200 rounded-lg text-sm text-red-800"
        >
          {invalidCount === 1
            ? '1 fält behöver rättas innan du kan gå vidare.'
            : `${invalidCount} fält behöver rättas innan du kan gå vidare.`}
        </div>
      )}

      {/* Current step content */}
      <div className="bg-white rounded-xl border shadow-sm p-6">
        <div className="mb-6">
          <h2
            ref={headingRef}
            tabIndex={-1}
            className="text-xl font-semibold text-gray-900 focus:outline-none"
          >
            {currentStep.name}
          </h2>
          {currentStep.description && (
            <p className="mt-1 text-gray-600">{currentStep.description}</p>
          )}
        </div>

        {/* Form fields */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4">
          {currentStep.queries.map((query) => (
            <QueryRenderer key={query.id} query={query} />
          ))}
        </div>
      </div>

      {/* Navigation buttons */}
      <div className="flex items-center justify-between pt-4">
        <div className="flex gap-3">
          {onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg
                       hover:bg-gray-50 transition-colors"
            >
              Avbryt
            </button>
          )}
          {onSaveDraft && (
            <button
              type="button"
              onClick={onSaveDraft}
              className="px-4 py-2 text-brand-600 bg-brand-50 border border-brand-200 rounded-lg
                       hover:bg-blue-100 transition-colors"
            >
              Spara utkast
            </button>
          )}
        </div>

        <div className="flex gap-3">
          {!isFirstStep && (
            <button
              type="button"
              onClick={handlePrevious}
              className="px-6 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg
                       hover:bg-gray-50 transition-colors flex items-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
              </svg>
              Föregående
            </button>
          )}

          {/* Separate keys stop React from reusing the "Nästa" button as the submit
              button, which would submit the form on the click that reaches the last step */}
          {isLastStep ? (
            <button
              key="submit"
              type="submit"
              disabled={isSubmitting}
              className="px-6 py-2 bg-green-700 text-white rounded-lg font-medium
                       hover:bg-green-800 transition-colors flex items-center gap-2
                       disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? (
                <>
                  <span className="animate-spin w-4 h-4 border-2 border-white border-t-transparent rounded-full" />
                  Skickar...
                </>
              ) : (
                <>
                  Skicka in
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </>
              )}
            </button>
          ) : (
            <button
              key="next"
              type="button"
              onClick={handleNext}
              className="px-6 py-2 bg-brand-600 text-white rounded-lg font-medium
                       hover:bg-brand-700 transition-colors flex items-center gap-2"
            >
              Nästa
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Progress text */}
      <div className="text-center text-sm text-gray-500">
        Steg {currentStepIndex + 1} av {steps.length}
      </div>
    </form>
  );
}
