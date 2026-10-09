'use client';

import {
  FileText,
  Send,
  RefreshCw,
  MessageCircle,
  Paperclip,
  CheckCircle,
  XCircle,
  Circle,
} from 'lucide-react';
import { format } from 'date-fns';
import { sv } from 'date-fns/locale';
import type { CaseEvent } from '@/lib/api/cases';

interface CaseTimelineProps {
  events: CaseEvent[];
}

type IconComponent = typeof FileText;

const eventIcons: Record<string, IconComponent> = {
  CREATED: FileText,
  SUBMITTED: Send,
  STATUS_CHANGED: RefreshCw,
  MESSAGE_SENT: MessageCircle,
  ATTACHMENT_ADDED: Paperclip,
  SIGNED: CheckCircle,
  PAYMENT_COMPLETED: CheckCircle,
  PAYMENT_FAILED: XCircle,
};

const eventIconColors: Record<string, string> = {
  CREATED: 'bg-gray-100 text-gray-700',
  SUBMITTED: 'bg-blue-100 text-blue-700',
  STATUS_CHANGED: 'bg-yellow-100 text-yellow-700',
  MESSAGE_SENT: 'bg-purple-100 text-purple-700',
  ATTACHMENT_ADDED: 'bg-indigo-100 text-indigo-700',
  SIGNED: 'bg-green-100 text-green-700',
  PAYMENT_COMPLETED: 'bg-green-100 text-green-700',
  PAYMENT_FAILED: 'bg-red-100 text-red-700',
};

function buildEventLabel(event: CaseEvent): string {
  if (event.description) return event.description;
  if (event.eventType === 'STATUS_CHANGED' && event.oldStatusName && event.newStatusName) {
    return `Status ändrad från ${event.oldStatusName} till ${event.newStatusName}`;
  }
  return event.eventType;
}

export function CaseTimeline({ events }: CaseTimelineProps) {
  if (events.length === 0) {
    return (
      <p className="text-gray-500 italic">Inga händelser ännu.</p>
    );
  }

  return (
    <ol
      className="relative border-l-2 border-gray-200 ml-4 space-y-6 pl-6"
      aria-label="Tidslinje för ärendehändelser"
    >
      {events.map((event) => {
        const Icon = eventIcons[event.eventType] ?? Circle;
        const iconColors = eventIconColors[event.eventType] ?? 'bg-gray-100 text-gray-700';
        const label = buildEventLabel(event);
        const date = new Date(event.createdAt);
        const dateLabel = format(date, "d MMMM yyyy 'kl.' HH:mm", { locale: sv });

        return (
          <li key={event.id} className="relative">
            <span
              className={`absolute -left-[34px] flex items-center justify-center w-8 h-8 rounded-full ring-4 ring-white ${iconColors}`}
              aria-hidden="true"
            >
              <Icon className="w-4 h-4" />
            </span>
            <div>
              <p className="font-medium text-gray-900">{label}</p>
              <p className="text-sm text-gray-500">
                <time dateTime={event.createdAt}>{dateLabel}</time>
                {event.actorName ? <> · {event.actorName}</> : null}
              </p>
              {event.comment ? (
                <p className="mt-2 text-sm text-gray-700 bg-gray-50 rounded px-3 py-2 border border-gray-200">
                  {event.comment}
                </p>
              ) : null}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
