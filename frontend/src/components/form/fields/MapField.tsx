'use client';

import React, { useState } from 'react';
import type { QueryDefinition } from '../types';
import { useFormContext } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface MapFieldProps {
  query: QueryDefinition;
}

/**
 * Map field component - allows user to select a location on a map.
 * Shows a simple coordinate input with future integration for actual map.
 */
export function MapField({ query }: MapFieldProps) {
  const { values, setValue, getFieldState } = useFormContext();
  const { state } = getFieldState(query.id);

  const value = (values[query.id] as {
    lat?: number;
    lng?: number;
    address?: string;
  }) || {};

  const [manualEntry, setManualEntry] = useState(false);

  const updateValue = (field: string, newValue: string | number) => {
    setValue(query.id, { ...value, [field]: newValue });
  };

  const isDisabled = state === 'DISABLED';
  const isReadonly = state === 'READONLY';

  return (
    <FieldWrapper query={{ ...query, width: 'FULL' }}>
      <div className="space-y-4">
        {/* Map placeholder */}
        <div className="bg-gray-100 border-2 border-dashed border-gray-300 rounded-lg p-8 text-center">
          <svg
            className="w-12 h-12 text-gray-400 mx-auto mb-3"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7"
            />
          </svg>
          <p className="text-gray-500 mb-2">Kartvisning kommer snart</p>
          <p className="text-sm text-gray-400">
            Klicka nedan för att ange koordinater manuellt
          </p>
        </div>

        {/* Toggle manual entry */}
        <button
          type="button"
          onClick={() => setManualEntry(!manualEntry)}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          {manualEntry ? 'Dölj manuell inmatning' : 'Ange koordinater manuellt'}
        </button>

        {/* Manual coordinate entry */}
        {manualEntry && (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Latitud {query.required && <span className="text-red-500">*</span>}
              </label>
              <input
                type="number"
                step="0.000001"
                value={value.lat || ''}
                onChange={(e) => updateValue('lat', parseFloat(e.target.value))}
                disabled={isDisabled}
                readOnly={isReadonly}
                required={query.required}
                placeholder="59.329323"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                           disabled:bg-gray-100 disabled:text-gray-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Longitud {query.required && <span className="text-red-500">*</span>}
              </label>
              <input
                type="number"
                step="0.000001"
                value={value.lng || ''}
                onChange={(e) => updateValue('lng', parseFloat(e.target.value))}
                disabled={isDisabled}
                readOnly={isReadonly}
                required={query.required}
                placeholder="18.068581"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                           disabled:bg-gray-100 disabled:text-gray-500"
              />
            </div>
          </div>
        )}

        {/* Address description */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Platsbeskrivning
          </label>
          <input
            type="text"
            value={value.address || ''}
            onChange={(e) => updateValue('address', e.target.value)}
            disabled={isDisabled}
            readOnly={isReadonly}
            placeholder="T.ex. Parkering vid ingång B"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                       disabled:bg-gray-100 disabled:text-gray-500"
          />
        </div>

        {/* Show coordinates if set */}
        {value.lat && value.lng && (
          <div className="text-sm text-gray-500 bg-gray-50 p-3 rounded-lg">
            Vald position: {value.lat.toFixed(6)}, {value.lng.toFixed(6)}
          </div>
        )}
      </div>
    </FieldWrapper>
  );
}
