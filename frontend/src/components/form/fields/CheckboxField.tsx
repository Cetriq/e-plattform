'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface CheckboxFieldProps {
  query: QueryDefinition;
}

export function CheckboxField({ query }: CheckboxFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.checked);
  };

  return (
    <FieldWrapper query={query}>
      <label className="flex items-start gap-3 cursor-pointer">
        <input
          id={query.id}
          type="checkbox"
          checked={Boolean(value)}
          onChange={handleChange}
          onBlur={onBlur}
          disabled={state === 'DISABLED'}
          required={query.required}
          className="mt-0.5 w-4 h-4 text-blue-600 border-gray-300 rounded
                     focus:ring-blue-500 disabled:opacity-50"
        />
        <span className="text-sm text-gray-700">
          {query.config.content || query.name}
          {query.required && <span className="text-red-500 ml-1">*</span>}
        </span>
      </label>
    </FieldWrapper>
  );
}
