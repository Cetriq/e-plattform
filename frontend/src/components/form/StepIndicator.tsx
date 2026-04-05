'use client';

import React from 'react';
import type { Step } from './types';

interface StepIndicatorProps {
  steps: Step[];
  currentStepIndex: number;
  onStepClick?: (index: number) => void;
  allowNavigation?: boolean;
}

export function StepIndicator({
  steps,
  currentStepIndex,
  onStepClick,
  allowNavigation = false,
}: StepIndicatorProps) {
  const handleClick = (index: number) => {
    if (allowNavigation && onStepClick && index <= currentStepIndex) {
      onStepClick(index);
    }
  };

  return (
    <nav aria-label="Formulärsteg" className="mb-8">
      <ol className="flex items-center">
        {steps.map((step, index) => {
          const isCompleted = index < currentStepIndex;
          const isCurrent = index === currentStepIndex;
          const isClickable = allowNavigation && index <= currentStepIndex;

          return (
            <li
              key={step.id}
              className={`flex items-center ${index < steps.length - 1 ? 'flex-1' : ''}`}
            >
              <button
                type="button"
                onClick={() => handleClick(index)}
                disabled={!isClickable}
                className={`
                  flex items-center gap-3 group
                  ${isClickable ? 'cursor-pointer' : 'cursor-default'}
                `}
              >
                <span
                  className={`
                    flex items-center justify-center w-10 h-10 rounded-full
                    text-sm font-semibold transition-colors
                    ${
                      isCompleted
                        ? 'bg-green-600 text-white'
                        : isCurrent
                        ? 'bg-blue-600 text-white ring-4 ring-blue-100'
                        : 'bg-gray-200 text-gray-500'
                    }
                    ${isClickable && !isCurrent ? 'group-hover:ring-2 group-hover:ring-blue-200' : ''}
                  `}
                >
                  {isCompleted ? (
                    <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                      <path
                        fillRule="evenodd"
                        d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                        clipRule="evenodd"
                      />
                    </svg>
                  ) : (
                    index + 1
                  )}
                </span>
                <span
                  className={`
                    hidden sm:block text-sm font-medium
                    ${
                      isCurrent
                        ? 'text-blue-600'
                        : isCompleted
                        ? 'text-green-600'
                        : 'text-gray-500'
                    }
                    ${isClickable && !isCurrent ? 'group-hover:text-blue-500' : ''}
                  `}
                >
                  {step.name}
                </span>
              </button>

              {index < steps.length - 1 && (
                <div
                  className={`
                    flex-1 h-0.5 mx-4
                    ${isCompleted ? 'bg-green-600' : 'bg-gray-200'}
                  `}
                />
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
