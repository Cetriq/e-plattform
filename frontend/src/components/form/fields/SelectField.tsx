'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface SelectFieldProps {
  query: QueryDefinition;
}

export function SelectField({ query }: SelectFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);
  const options = query.config.options || [];

  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onChange(e.target.value);
  };

  return (
    <FieldWrapper query={query}>
      <select
        id={query.id}
        value={(value as string) ?? ''}
        onChange={handleChange}
        onBlur={onBlur}
        disabled={state === 'DISABLED'}
        required={query.required}
        className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                   focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                   disabled:bg-gray-100 disabled:cursor-not-allowed"
      >
        <option value="">{query.placeholder || 'Välj...'}</option>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </FieldWrapper>
  );
}
