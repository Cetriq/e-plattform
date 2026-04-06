'use client';

import React from 'react';
import type { QueryDefinition } from '../types';
import { useFormContext } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';

interface OrganizationFieldProps {
  query: QueryDefinition;
}

/**
 * Organization field component - collects organization information.
 * Includes organization number, name, and contact details.
 */
export function OrganizationField({ query }: OrganizationFieldProps) {
  const { values, setValue, getFieldState } = useFormContext();
  const { state } = getFieldState(query.id);

  const value = (values[query.id] as {
    organizationNumber?: string;
    name?: string;
    contactPerson?: string;
    email?: string;
    phone?: string;
    address?: string;
    postalCode?: string;
    city?: string;
  }) || {};

  const updateValue = (field: string, newValue: string) => {
    setValue(query.id, { ...value, [field]: newValue });
  };

  const isDisabled = state === 'DISABLED';
  const isReadonly = state === 'READONLY';

  return (
    <FieldWrapper query={{ ...query, width: 'FULL' }}>
      <div className="space-y-4">
        {/* Organization info row */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Organisationsnummer {query.required && <span className="text-red-500">*</span>}
            </label>
            <input
              type="text"
              value={value.organizationNumber || ''}
              onChange={(e) => updateValue('organizationNumber', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              required={query.required}
              placeholder="XXXXXX-XXXX"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Organisationsnamn {query.required && <span className="text-red-500">*</span>}
            </label>
            <input
              type="text"
              value={value.name || ''}
              onChange={(e) => updateValue('name', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              required={query.required}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
        </div>

        {/* Contact person */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Kontaktperson
          </label>
          <input
            type="text"
            value={value.contactPerson || ''}
            onChange={(e) => updateValue('contactPerson', e.target.value)}
            disabled={isDisabled}
            readOnly={isReadonly}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                       disabled:bg-gray-100 disabled:text-gray-500"
          />
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

        {/* Address */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Adress
          </label>
          <input
            type="text"
            value={value.address || ''}
            onChange={(e) => updateValue('address', e.target.value)}
            disabled={isDisabled}
            readOnly={isReadonly}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                       disabled:bg-gray-100 disabled:text-gray-500"
          />
        </div>

        {/* Postal row */}
        <div className="grid grid-cols-3 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Postnummer
            </label>
            <input
              type="text"
              value={value.postalCode || ''}
              onChange={(e) => updateValue('postalCode', e.target.value)}
              disabled={isDisabled}
              readOnly={isReadonly}
              placeholder="XXX XX"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                         disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>
          <div className="col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Ort
            </label>
            <input
              type="text"
              value={value.city || ''}
              onChange={(e) => updateValue('city', e.target.value)}
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
