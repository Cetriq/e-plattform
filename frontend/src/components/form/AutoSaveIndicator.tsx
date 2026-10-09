'use client';

import { Check, Loader2, AlertTriangle, CloudOff } from 'lucide-react';
import { format } from 'date-fns';
import { sv } from 'date-fns/locale';

export type AutoSaveStatus = 'idle' | 'saving' | 'saved' | 'error';

interface AutoSaveIndicatorProps {
  status: AutoSaveStatus;
  lastSavedAt: Date | null;
  onRetry?: () => void;
  className?: string;
}

export function AutoSaveIndicator({
  status,
  lastSavedAt,
  onRetry,
  className,
}: AutoSaveIndicatorProps) {
  const wrapperClass = [
    'flex items-center gap-2 text-sm rounded-md px-3 py-1.5',
    className ?? '',
  ].join(' ');

  if (status === 'saving') {
    return (
      <div
        role="status"
        aria-live="polite"
        className={`${wrapperClass} bg-brand-50 text-brand-700`}
      >
        <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
        <span>Sparar utkast…</span>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div
        role="status"
        aria-live="assertive"
        className={`${wrapperClass} bg-red-50 text-red-700`}
      >
        <AlertTriangle className="w-4 h-4" aria-hidden="true" />
        <span>Kunde inte spara utkast.</span>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="underline font-medium hover:text-red-900"
          >
            Försök igen
          </button>
        )}
      </div>
    );
  }

  if (status === 'saved' && lastSavedAt) {
    const timeLabel = format(lastSavedAt, "HH:mm", { locale: sv });
    return (
      <div
        role="status"
        aria-live="polite"
        className={`${wrapperClass} bg-green-50 text-green-700`}
      >
        <Check className="w-4 h-4" aria-hidden="true" />
        <span>Utkast sparat kl {timeLabel}</span>
      </div>
    );
  }

  if (lastSavedAt) {
    const timeLabel = format(lastSavedAt, "HH:mm", { locale: sv });
    return (
      <div
        role="status"
        aria-live="polite"
        className={`${wrapperClass} bg-gray-50 text-gray-600`}
      >
        <Check className="w-4 h-4 text-gray-500" aria-hidden="true" />
        <span>Senast sparat kl {timeLabel}</span>
      </div>
    );
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className={`${wrapperClass} bg-gray-50 text-gray-500`}
    >
      <CloudOff className="w-4 h-4" aria-hidden="true" />
      <span>Inte sparat ännu</span>
    </div>
  );
}
