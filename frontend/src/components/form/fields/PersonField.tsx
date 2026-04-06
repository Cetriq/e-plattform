'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useFormContext } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface PersonFieldProps {
  query: QueryDefinition;
}

/**
 * Person field component - collects personal information.
 * Includes personal number (personnummer), name, email, and phone.
 */
export function PersonField({ query }: PersonFieldProps) {
  const { values, setValue, getFieldState } = useFormContext();
  const { state } = getFieldState(query.id);

  const value = (values[query.id] as {
    personalNumber?: string;
    firstName?: string;
    lastName?: string;
    email?: string;
    phone?: string;
  }) || {};

  const updateValue = (field: string, newValue: string) => {
    setValue(query.id, { ...value, [field]: newValue });
  };

  const isDisabled = state === 'DISABLED';
  const isReadonly = state === 'READONLY';

  return (
    <FieldWrapper query={{ ...query, width: 'FULL' }}>
      <div className="space-y-4">
        {/* Personal Number */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Personnummer {query.required && <span className="text-red-500">*</span>}
          </label>
          <input
            type="text"
            value={value.personalNumber || ''}
            onChange={(e) => updateValue('personalNumber', e.target.value)}
            disabled={isDisabled}
            readOnly={isReadonly}
            required={query.required}
            placeholder="ÅÅÅÅMMDD-XXXX"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                       disabled:bg-gray-100 disabled:text-gray-500"
          />
        </div>

        {/* Name row */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Förnamn {query.required && <span className="text-red-500">*</span>}
            </label>
            <input
              type="text"
              value={value.firstName || ''}
              onChange={(e) => updateValue('firstName', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              required={query.required}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Efternamn {query.required && <span className="text-red-500">*</span>}
            </label>
            <input
              type="text"
              value={value.lastName || ''}
              onChange={(e) => updateValue('lastName', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              required={query.required}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
        </div>

        {/* Contact row */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              E-post
            </label>
            <input
              type="email"
              value={value.email || ''}
              onChange={(e) => updateValue('email', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Telefon
            </label>
            <input
              type="tel"
              value={value.phone || ''}
              onChange={(e) => updateValue('phone', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
        </div>
      </div>
    </FieldWrapper>
  );
}
