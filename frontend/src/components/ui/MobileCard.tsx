'use client';

import React from 'react';
import Link from 'next/link';

interface MobileCardAction {
  label: string;
  href?: string;
  onClick?: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
}

interface MobileCardField {
  label: string;
  value: React.ReactNode;
  fullWidth?: boolean;
}

interface MobileCardProps {
  title: string;
  subtitle?: string;
  status?: {
    label: string;
    color?: string;
    bgColor?: string;
  };
  fields: MobileCardField[];
  actions?: MobileCardAction[];
  href?: string;
}

const actionStyles: Record<string, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700',
  secondary: 'bg-gray-100 text-gray-700 hover:bg-gray-200',
  danger: 'bg-red-50 text-red-700 hover:bg-red-100',
};

export function MobileCard({
  title,
  subtitle,
  status,
  fields,
  actions = [],
  href,
}: MobileCardProps) {
  const CardWrapper = href
    ? ({ children }: { children: React.ReactNode }) => (
        <Link href={href} className="block">
          {children}
        </Link>
      )
    : ({ children }: { children: React.ReactNode }) => <>{children}</>;

  return (
    <div className="bg-white border rounded-lg shadow-sm hover:shadow-md transition-shadow">
      <CardWrapper>
        <div className="p-4">
          {/* Header */}
          <div className="flex items-start justify-between gap-3 mb-3">
            <div className="min-w-0 flex-1">
              <h3 className="font-medium text-gray-900 truncate">{title}</h3>
              {subtitle && (
                <p className="text-sm text-gray-500 truncate">{subtitle}</p>
              )}
            </div>
            {status && (
              <span
                className="flex-shrink-0 inline-flex px-2.5 py-1 text-xs font-medium rounded-full"
                style={{
                  backgroundColor: status.bgColor || '#E5E7EB',
                  color: status.color || '#374151',
                }}
              >
                {status.label}
              </span>
            )}
          </div>

          {/* Fields grid */}
          <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            {fields.map((field, index) => (
              <div
                key={index}
                className={field.fullWidth ? 'col-span-2' : ''}
              >
                <dt className="text-gray-500 text-xs">{field.label}</dt>
                <dd className="text-gray-900">{field.value}</dd>
              </div>
            ))}
          </div>
        </div>
      </CardWrapper>

      {/* Actions */}
      {actions.length > 0 && (
        <div className="border-t px-4 py-3 flex gap-2 justify-end">
          {actions.map((action, index) => {
            const className = `px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
              actionStyles[action.variant || 'secondary']
            }`;

            if (action.href) {
              return (
                <Link key={index} href={action.href} className={className}>
                  {action.label}
                </Link>
              );
            }

            return (
              <button
                key={index}
                type="button"
                onClick={action.onClick}
                className={className}
              >
                {action.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

// Convenience component for a list of mobile cards
interface MobileCardListProps {
  children: React.ReactNode;
  emptyMessage?: string;
  isEmpty?: boolean;
}

export function MobileCardList({
  children,
  emptyMessage = 'Inga objekt att visa',
  isEmpty = false,
}: MobileCardListProps) {
  if (isEmpty) {
    return (
      <div className="bg-white border rounded-lg p-8 text-center">
        <p className="text-gray-500">{emptyMessage}</p>
      </div>
    );
  }

  return <div className="space-y-3">{children}</div>;
}
