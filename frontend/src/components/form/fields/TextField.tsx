'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface TextFieldProps {
  query: QueryDefinition;
}

export function TextField({ query }: TextFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);

  const inputType: Record<string, string> = {
    TEXT: 'text',
    EMAIL: 'email',
    PHONE: 'tel',
    URL: 'url',
    NUMBER: 'number',
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = query.queryType === 'NUMBER' ? parseFloat(e.target.value) || '' : e.target.value;
    onChange(newValue);
  };

  return (
    <FieldWrapper query={query}>
      <input
        id={query.id}
        type={inputType[query.queryType] || 'text'}
        value={(value as string) ?? ''}
        onChange={handleChange}
        onBlur={onBlur}
        placeholder={query.placeholder}
        disabled={state === 'DISABLED'}
        readOnly={state === 'READONLY'}
        required={query.required}
        maxLength={query.config.maxLength}
        minLength={query.config.minLength}
        pattern={query.config.pattern}
        min={query.config.min}
        max={query.config.max}
        step={query.config.step}
        className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                   focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                   disabled:bg-gray-100 disabled:cursor-not-allowed
                   read-only:bg-gray-50"
      />
    </FieldWrapper>
  );
}
