'use client';

import React from 'react';
import type { QueryDefinition, QueryState } from '../types';
import { useField } from '../FormContext';

interface FieldWrapperProps {
  query: QueryDefinition;
  children: React.ReactNode;
}

export function FieldWrapper({ query, children }: FieldWrapperProps) {
  const { state, errors, touched } = useField(query.id);

  if (state === 'HIDDEN') {
    return null;
  }

  const showError = touched && errors.length > 0;
  // Field is required if either the base definition says so OR the computed state is VISIBLE_REQUIRED
  const isRequired = query.required || state === 'VISIBLE_REQUIRED';

  const widthClasses: Record<string, string> = {
    FULL: 'col-span-full',
    HALF: 'col-span-full md:col-span-1',
    THIRD: 'col-span-full md:col-span-1 lg:col-span-1',
  };

  return (
    <div className={`${widthClasses[query.width] || widthClasses.FULL}`}>
      <div className="space-y-1">
        {query.name && !isLayoutElement(query.queryType) && (
          <label
            htmlFor={query.id}
            className="block text-sm font-medium text-gray-700"
          >
            {query.name}
            {isRequired && (
              <span className="text-red-500 ml-1">*</span>
            )}
          </label>
        )}

        {query.description && (
          <p className="text-sm text-gray-500">{query.description}</p>
        )}

        {children}

        {showError && (
          <div className="text-sm text-red-600" role="alert">
            {errors.map((error, i) => (
              <p key={i}>{error}</p>
            ))}
          </div>
        )}

        {query.helpText && (
          <p className="text-xs text-gray-400">{query.helpText}</p>
        )}
      </div>
    </div>
  );
}

function isLayoutElement(queryType: string): boolean {
  return ['HEADING', 'PARAGRAPH', 'DIVIDER'].includes(queryType);
}
