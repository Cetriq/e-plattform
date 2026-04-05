'use client';

import React from 'react';
import type { QueryDefinition } from './types';
import { useFormContext } from './FormContext';
import {
  TextField,
  TextareaField,
  SelectField,
  MultiSelectField,
  RadioField,
  CheckboxField,
  DateField,
  FileField,
  HeadingElement,
  ParagraphElement,
  DividerElement,
} from './fields';

interface QueryRendererProps {
  query: QueryDefinition;
}

export function QueryRenderer({ query }: QueryRendererProps) {
  const { getFieldState, userId, caseId } = useFormContext();
  const fieldState = getFieldState(query.id);

  // Don't render hidden fields
  if (fieldState.state === 'HIDDEN') {
    return null;
  }
  switch (query.queryType) {
    // Text inputs
    case 'TEXT':
    case 'EMAIL':
    case 'PHONE':
    case 'URL':
    case 'NUMBER':
      return <TextField query={query} />;

    case 'TEXTAREA':
      return <TextareaField query={query} />;

    // Selection
    case 'SELECT':
      return <SelectField query={query} />;

    case 'MULTISELECT':
      return <MultiSelectField query={query} />;

    case 'RADIO':
      return <RadioField query={query} />;

    case 'CHECKBOX':
      return <CheckboxField query={query} />;

    // Date/Time
    case 'DATE':
    case 'DATETIME':
    case 'TIME':
      return <DateField query={query} />;

    // Files
    case 'FILE':
    case 'IMAGE':
      return <FileField query={query} userId={userId} caseId={caseId} />;

    // Layout elements
    case 'HEADING':
      return <HeadingElement query={query} />;

    case 'PARAGRAPH':
      return <ParagraphElement query={query} />;

    case 'DIVIDER':
      return <DividerElement query={query} />;

    // Special types (to be implemented)
    case 'MAP':
    case 'LOCATION':
    case 'SIGNATURE':
    case 'ORGANIZATION':
    case 'PERSON':
      return (
        <div className="col-span-full p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <p className="text-sm text-yellow-800">
            Fälttyp &quot;{query.queryType}&quot; stöds inte ännu.
          </p>
        </div>
      );

    default:
      return (
        <div className="col-span-full p-4 bg-red-50 border border-red-200 rounded-lg">
          <p className="text-sm text-red-800">
            Okänd fälttyp: {query.queryType}
          </p>
        </div>
      );
  }
}
