'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface RadioFieldProps {
  query: QueryDefinition;
}

export function RadioField({ query }: RadioFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);
  const options = query.config.options || [];

  const handleChange = (optValue: string) => {
    onChange(optValue);
  };

  return (
    <FieldWrapper query={query}>
      <div className="space-y-2">
        {options.map((opt) => (
          <label
            key={opt.value}
            className="flex items-center gap-2 cursor-pointer"
          >
            <input
              type="radio"
              name={query.id}
              value={opt.value}
              checked={value === opt.value}
              onChange={() => handleChange(opt.value)}
              onBlur={onBlur}
              disabled={state === 'DISABLED'}
              className="w-4 h-4 text-blue-600 border-gray-300
                         focus:ring-blue-500 disabled:opacity-50"
            />
            <span className="text-sm text-gray-700">{opt.label}</span>
          </label>
        ))}
      </div>
    </FieldWrapper>
  );
}
