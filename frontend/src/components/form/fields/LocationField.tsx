'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useFormContext } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface LocationFieldProps {
  query: QueryDefinition;
}

/**
 * Location field component - collects address/location information.
 * Includes street address, postal code, city, and optionally coordinates.
 */
export function LocationField({ query }: LocationFieldProps) {
  const { values, setValue, getFieldState } = useFormContext();
  const { state } = getFieldState(query.id);

  const value = (values[query.id] as {
    address?: string;
    postalCode?: string;
    city?: string;
    municipality?: string;
    coordinates?: { lat: number; lng: number };
  }) || {};

  const updateValue = (field: string, newValue: string | { lat: number; lng: number }) => {
    setValue(query.id, { ...value, [field]: newValue });
  };

  const isDisabled = state === 'DISABLED';
  const isReadonly = state === 'READONLY';

  return (
    <FieldWrapper query={{ ...query, width: 'FULL' }}>
      <div className="space-y-4">
        {/* Address */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Gatuadress {query.required && <span className="text-red-500">*</span>}
          </label>
          <input
            type="text"
            value={value.address || ''}
            onChange={(e) => updateValue('address', e.target.value)}
            disabled={isDisabled}
            readOnly={isReadonly}
            required={query.required}
            placeholder="Gatunamn och nummer"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                       disabled:bg-gray-100 disabled:text-gray-500"
          />
        </div>

        {/* Postal row */}
        <div className="grid grid-cols-3 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Postnummer {query.required && <span className="text-red-500">*</span>}
            </label>
            <input
              type="text"
              value={value.postalCode || ''}
              onChange={(e) => updateValue('postalCode', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              required={query.required}
              placeholder="XXX XX"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
          <div className="col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Ort {query.required && <span className="text-red-500">*</span>}
            </label>
            <input
              type="text"
              value={value.city || ''}
              onChange={(e) => updateValue('city', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              required={query.required}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
        </div>

        {/* Municipality (optional) */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Kommun
          </label>
          <input
            type="text"
            value={value.municipality || ''}
            onChange={(e) => updateValue('municipality', e.target.value)}
            disabled={isDisabled}
            readOnly={isReadonly}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                       disabled:bg-gray-100 disabled:text-gray-500"
          />
        </div>

        {/* Coordinates display (if set) */}
        {value.coordinates && (
          <div className="text-sm text-gray-500">
            Koordinater: {value.coordinates.lat.toFixed(6)}, {value.coordinates.lng.toFixed(6)}
          </div>
        )}
      </div>
    </FieldWrapper>
  );
}
