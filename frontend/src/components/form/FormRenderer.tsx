'use client';

import React, { useState, useCallback } from 'react';
import type { Flow, Step, FormValues } from './types';
import { FormProvider } from './FormContext';
import { StepIndicator } from './StepIndicator';
import { QueryRenderer } from './QueryRenderer';

interface FormRendererProps {
  flow: Flow;
  initialValues?: FormValues;
  onSubmit: (values: FormValues) => void;
  onSaveDraft?: (values: FormValues) => void;
  onCancel?: () => void;
  userId?: string;
  caseId?: string;
}

export function FormRenderer({
  flow,
  initialValues = {},
  onSubmit,
  onSaveDraft,
  onCancel,
  userId,
  caseId,
}: FormRendererProps) {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [values, setValues] = useState<FormValues>(initialValues);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const steps = flow.steps;
  const currentStep = steps[currentStepIndex];
  const isFirstStep = currentStepIndex === 0;
  const isLastStep = currentStepIndex === steps.length - 1;

  const handleValuesChange = useCallback((newValues: FormValues) => {
    setValues(newValues);
  }, []);

  const handlePrevious = () => {
    if (!isFirstStep) {
      setCurrentStepIndex((prev) => prev - 1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleNext = () => {
    // TODO: Validate current step before proceeding
    if (!isLastStep) {
      setCurrentStepIndex((prev) => prev + 1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // TODO: Full validation before submit
    setIsSubmitting(true);
    try {
      await onSubmit(values);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSaveDraft = () => {
    onSaveDraft?.(values);
  };

  const handleStepClick = (index: number) => {
    setCurrentStepIndex(index);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <FormProvider steps={steps} initialValues={values} onChange={handleValuesChange} userId={userId} caseId={caseId}>
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Step indicator */}
        {steps.length > 1 && (
          <StepIndicator
            steps={steps}
            currentStepIndex={currentStepIndex}
            onStepClick={handleStepClick}
            allowNavigation={true}
          />
        )}

        {/* Current step content */}
        <div className="bg-white rounded-xl border shadow-sm p-6">
          <div className="mb-6">
            <h2 className="text-xl font-semibold text-gray-900">{currentStep.name}</h2>
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
                onClick={handleSaveDraft}
                className="px-4 py-2 text-blue-600 bg-blue-50 border border-blue-200 rounded-lg
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

            {isLastStep ? (
              <button
                type="submit"
                disabled={isSubmitting}
                className="px-6 py-2 bg-green-600 text-white rounded-lg font-medium
                         hover:bg-green-700 transition-colors flex items-center gap-2
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
                type="button"
                onClick={handleNext}
                className="px-6 py-2 bg-blue-600 text-white rounded-lg font-medium
                         hover:bg-blue-700 transition-colors flex items-center gap-2"
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
    </FormProvider>
  );
}
