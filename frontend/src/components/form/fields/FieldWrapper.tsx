'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';

interface FieldWrapperProps {
  query: QueryDefinition;
  children: React.ReactNode;
}

export function FieldWrapper({ query, children }: FieldWrapperProps) {
  const { state, errors, touched, value } = useField(query.id);

  if (state === 'HIDDEN') {
    return null;
  }

  const showError = touched && errors.length > 0;
  // Field is required if either the base definition says so OR the computed state is VISIBLE_REQUIRED
  const isRequired = query.required || state === 'VISIBLE_REQUIRED';

  // Determine validation state for visual indicators
  const validationState = getValidationState(touched, errors, value);

  const widthClasses: Record<string, string> = {
    FULL: 'col-span-full',
    HALF: 'col-span-full md:col-span-1',
    THIRD: 'col-span-full md:col-span-1 lg:col-span-1',
  };

  // Generate unique IDs for ARIA
  const errorId = `${query.id}-error`;
  const descriptionId = `${query.id}-description`;
  const helpTextId = `${query.id}-help`;

  // Build aria-describedby list
  const ariaDescribedBy = [
    query.description ? descriptionId : null,
    query.helpText ? helpTextId : null,
    showError ? errorId : null,
  ]
    .filter(Boolean)
    .join(' ') || undefined;

  return (
    <div className={`${widthClasses[query.width] || widthClasses.FULL}`}>
      <div className="space-y-1">
        {query.name && !isLayoutElement(query.queryType) && (
          <label
            htmlFor={query.id}
            className="flex items-center gap-2 text-sm font-medium text-gray-700"
          >
            <span>{query.name}</span>
            {isRequired && (
              <span className="text-red-500" aria-label="obligatoriskt fält">*</span>
            )}
            {/* Validation indicator */}
            {touched && validationState !== 'neutral' && (
              <span
                className={`inline-flex items-center justify-center w-4 h-4 rounded-full ${
                  validationState === 'valid'
                    ? 'bg-green-100 text-green-600'
                    : 'bg-red-100 text-red-600'
                }`}
                aria-hidden="true"
              >
                {validationState === 'valid' ? (
                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                  </svg>
                ) : (
                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                )}
              </span>
            )}
          </label>
        )}

        {query.description && (
          <p id={descriptionId} className="text-sm text-gray-500">
            {query.description}
          </p>
        )}

        {/* Clone children to inject ARIA attributes */}
        {React.Children.map(children, (child) => {
          if (React.isValidElement(child)) {
            return React.cloneElement(child as React.ReactElement<Record<string, unknown>>, {
              'aria-invalid': showError ? 'true' : undefined,
              'aria-describedby': ariaDescribedBy,
              'aria-required': isRequired ? 'true' : undefined,
              className: `${(child.props as { className?: string }).className || ''} ${
                showError
                  ? 'border-red-500 focus:ring-red-500 focus:border-red-500'
                  : validationState === 'valid'
                  ? 'border-green-500 focus:ring-green-500 focus:border-green-500'
                  : ''
              }`.trim(),
            });
          }
          return child;
        })}

        {showError && (
          <div
            id={errorId}
            className="flex items-center gap-1 text-sm text-red-600"
            role="alert"
            aria-live="polite"
          >
            <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <div>
              {errors.map((error, i) => (
                <p key={i}>{error}</p>
              ))}
            </div>
          </div>
        )}

        {query.helpText && (
          <p id={helpTextId} className="text-xs text-gray-400">
            {query.helpText}
          </p>
        )}
      </div>
    </div>
  );
}

function getValidationState(
  touched: boolean,
  errors: string[],
  value: unknown
): 'neutral' | 'valid' | 'invalid' {
  if (!touched) return 'neutral';
  if (errors.length > 0) return 'invalid';
  // Only show valid state if field has a value
  if (value !== undefined && value !== null && value !== '') {
    return 'valid';
  }
  return 'neutral';
}

function isLayoutElement(queryType: string): boolean {
  return ['HEADING', 'PARAGRAPH', 'DIVIDER'].includes(queryType);
}
