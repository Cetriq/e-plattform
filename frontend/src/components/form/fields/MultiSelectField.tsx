'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface MultiSelectFieldProps {
  query: QueryDefinition;
}

export function MultiSelectField({ query }: MultiSelectFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);
  const options = query.config.options || [];
  const selectedValues = (value as string[]) || [];

  const handleChange = (optValue: string, checked: boolean) => {
    if (checked) {
      onChange([...selectedValues, optValue]);
    } else {
      onChange(selectedValues.filter((v) => v !== optValue));
    }
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
              type="checkbox"
              checked={selectedValues.includes(opt.value)}
              onChange={(e) => handleChange(opt.value, e.target.checked)}
              onBlur={onBlur}
              disabled={state === 'DISABLED'}
              className="w-4 h-4 text-blue-600 border-gray-300 rounded
                         focus:ring-blue-500 disabled:opacity-50"
            />
            <span className="text-sm text-gray-700">{opt.label}</span>
          </label>
        ))}
      </div>
    </FieldWrapper>
  );
}
