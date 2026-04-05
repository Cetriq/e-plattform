'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface TextareaFieldProps {
  query: QueryDefinition;
}

export function TextareaField({ query }: TextareaFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    onChange(e.target.value);
  };

  return (
    <FieldWrapper query={query}>
      <textarea
        id={query.id}
        value={(value as string) ?? ''}
        onChange={handleChange}
        onBlur={onBlur}
        placeholder={query.placeholder}
        disabled={state === 'DISABLED'}
        readOnly={state === 'READONLY'}
        required={query.required}
        maxLength={query.config.maxLength}
        minLength={query.config.minLength}
        rows={4}
        className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                   focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                   disabled:bg-gray-100 disabled:cursor-not-allowed
                   read-only:bg-gray-50 resize-y min-h-[100px]"
      />
    </FieldWrapper>
  );
}
