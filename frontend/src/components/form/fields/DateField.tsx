'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface DateFieldProps {
  query: QueryDefinition;
}

export function DateField({ query }: DateFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);

  const inputType: Record<string, string> = {
    DATE: 'date',
    DATETIME: 'datetime-local',
    TIME: 'time',
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value);
  };

  return (
    <FieldWrapper query={query}>
      <input
        id={query.id}
        type={inputType[query.queryType] || 'date'}
        value={(value as string) ?? ''}
        onChange={handleChange}
        onBlur={onBlur}
        disabled={state === 'DISABLED'}
        readOnly={state === 'READONLY'}
        required={query.required}
        className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                   focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                   disabled:bg-gray-100 disabled:cursor-not-allowed
                   read-only:bg-gray-50"
      />
    </FieldWrapper>
  );
}
