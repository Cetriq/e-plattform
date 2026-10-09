import type { QueryDefinition, QueryType } from './types';

const LAYOUT_TYPES: QueryType[] = ['HEADING', 'PARAGRAPH', 'DIVIDER'];

/** Sub-fields that must be filled in when a composite field is required. */
const REQUIRED_PARTS: Partial<Record<QueryType, { key: string; label: string }[]>> = {
  PERSON: [
    { key: 'personalNumber', label: 'Personnummer' },
    { key: 'firstName', label: 'Förnamn' },
    { key: 'lastName', label: 'Efternamn' },
  ],
  ORGANIZATION: [
    { key: 'organizationNumber', label: 'Organisationsnummer' },
    { key: 'name', label: 'Organisationsnamn' },
  ],
  LOCATION: [
    { key: 'address', label: 'Gatuadress' },
    { key: 'postalCode', label: 'Postnummer' },
    { key: 'city', label: 'Ort' },
  ],
  MAP: [
    { key: 'lat', label: 'Latitud' },
    { key: 'lng', label: 'Longitud' },
  ],
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^\+?[\d\s\-()]{6,20}$/;

export function isEmptyValue(value: unknown): boolean {
  if (value === undefined || value === null || value === false) return true;
  if (typeof value === 'number') return Number.isNaN(value);
  if (typeof value === 'string') return value.trim() === '';
  if (Array.isArray(value)) return value.length === 0;
  if (typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).every(isEmptyValue);
  }
  return false;
}

/**
 * Luhn checksum over the last 10 digits, used by both Swedish
 * personnummer and organisationsnummer.
 */
function hasValidLuhn(digits: string): boolean {
  const ten = digits.slice(-10);
  let sum = 0;
  for (let i = 0; i < 10; i++) {
    let d = Number(ten[i]) * (i % 2 === 0 ? 2 : 1);
    if (d > 9) d -= 9;
    sum += d;
  }
  return sum % 10 === 0;
}

export function isValidPersonnummer(input: string): boolean {
  const match = input.trim().match(/^(\d{2})?(\d{6})[-+]?(\d{4})$/);
  if (!match) return false;
  return hasValidLuhn(match[2] + match[3]);
}

export function isValidOrganisationsnummer(input: string): boolean {
  const match = input.trim().match(/^(\d{2})?(\d{6})-?(\d{4})$/);
  if (!match) return false;
  return hasValidLuhn(match[2] + match[3]);
}

/**
 * Returns the validation errors for a single field. An empty array means
 * the value is valid. Hidden fields should not be passed here.
 */
export function validateQuery(
  query: QueryDefinition,
  value: unknown,
  isRequired: boolean
): string[] {
  if (LAYOUT_TYPES.includes(query.queryType)) return [];

  const errors: string[] = [];
  const parts = REQUIRED_PARTS[query.queryType];

  if (isRequired) {
    if (parts) {
      const record = (value as Record<string, unknown>) || {};
      const missing = parts.filter((p) => isEmptyValue(record[p.key])).map((p) => p.label);
      if (missing.length > 0) {
        errors.push(`Fyll i: ${missing.join(', ')}`);
      }
    } else if (isEmptyValue(value)) {
      errors.push(
        query.queryType === 'CHECKBOX' ? 'Du måste kryssa i rutan' : 'Fältet är obligatoriskt'
      );
    }
  }

  if (isEmptyValue(value)) return errors;

  const { config = {} } = query;

  if (typeof value === 'string') {
    if (config.minLength && value.length < config.minLength) {
      errors.push(`Minst ${config.minLength} tecken`);
    }
    if (config.maxLength && value.length > config.maxLength) {
      errors.push(`Högst ${config.maxLength} tecken`);
    }
    if (config.pattern) {
      try {
        if (!new RegExp(`^(?:${config.pattern})$`).test(value)) {
          errors.push('Felaktigt format');
        }
      } catch {
        // Invalid pattern in the flow definition, ignore rather than block the user
      }
    }
    if (query.queryType === 'EMAIL' && !EMAIL_PATTERN.test(value.trim())) {
      errors.push('Ange en giltig e-postadress');
    }
    if (query.queryType === 'PHONE' && !PHONE_PATTERN.test(value.trim())) {
      errors.push('Ange ett giltigt telefonnummer');
    }
    if (query.queryType === 'URL') {
      try {
        new URL(value);
      } catch {
        errors.push('Ange en giltig webbadress, t.ex. https://exempel.se');
      }
    }
  }

  if (query.queryType === 'NUMBER' && typeof value === 'number') {
    if (config.min !== undefined && value < config.min) errors.push(`Lägsta värde är ${config.min}`);
    if (config.max !== undefined && value > config.max) errors.push(`Högsta värde är ${config.max}`);
  }

  if (typeof value === 'object' && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    const pnr = record.personalNumber;
    if (query.queryType === 'PERSON' && typeof pnr === 'string' && pnr.trim() && !isValidPersonnummer(pnr)) {
      errors.push('Ogiltigt personnummer');
    }
    const orgnr = record.organizationNumber;
    if (
      query.queryType === 'ORGANIZATION' &&
      typeof orgnr === 'string' &&
      orgnr.trim() &&
      !isValidOrganisationsnummer(orgnr)
    ) {
      errors.push('Ogiltigt organisationsnummer');
    }
    const email = record.email;
    if (typeof email === 'string' && email.trim() && !EMAIL_PATTERN.test(email.trim())) {
      errors.push('Ange en giltig e-postadress');
    }
  }

  return errors;
}
