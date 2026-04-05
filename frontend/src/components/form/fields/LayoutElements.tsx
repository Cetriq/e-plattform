'use client';

import React from 'react';
import type { QueryDefinition } from '../types';

interface LayoutElementProps {
  query: QueryDefinition;
}

export function HeadingElement({ query }: LayoutElementProps) {
  const level = query.config.level || 2;
  const content = query.name || query.config.content || '';

  const headingClasses: Record<number, string> = {
    1: 'text-2xl font-bold text-gray-900',
    2: 'text-xl font-semibold text-gray-800',
    3: 'text-lg font-semibold text-gray-800',
    4: 'text-base font-medium text-gray-700',
  };

  return (
    <div className="col-span-full">
      <h2 className={headingClasses[level] || headingClasses[2]}>{content}</h2>
      {query.description && (
        <p className="mt-1 text-sm text-gray-500">{query.description}</p>
      )}
    </div>
  );
}

export function ParagraphElement({ query }: LayoutElementProps) {
  const content = query.config.content || query.description || '';

  return (
    <div className="col-span-full">
      <p className="text-gray-600 whitespace-pre-wrap">{content}</p>
    </div>
  );
}

export function DividerElement({ query }: LayoutElementProps) {
  return (
    <div className="col-span-full py-2">
      <hr className="border-gray-200" />
    </div>
  );
}
