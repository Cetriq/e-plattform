'use client';

import { useEffect, useId, useRef, useState } from 'react';

export interface ThreadMessage {
  id: string;
  message: string;
  createdAt: string;
  authorName: string;
  /** True when the current viewer wrote the message (shown on the right). */
  mine: boolean;
}

interface MessageThreadProps {
  messages: ThreadMessage[];
  /** Hide the composer when sending isn't possible. */
  onSend?: (text: string) => Promise<void>;
  placeholder?: string;
  sendLabel?: string;
  emptyText: string;
  /** Shown instead of the composer when onSend is not set. */
  disabledText?: string;
  /** Style for internal notes. */
  variant?: 'conversation' | 'notes';
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('sv-SE', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * A list of messages with a box for writing a new one.
 */
export function MessageThread({
  messages,
  onSend,
  placeholder = 'Skriv ett meddelande…',
  sendLabel = 'Skicka',
  emptyText,
  disabledText,
  variant = 'conversation',
}: MessageThreadProps) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useRef<HTMLOListElement>(null);
  const inputId = useId();

  // Keep the newest message in view
  useEffect(() => {
    listRef.current?.lastElementChild?.scrollIntoView({ block: 'nearest' });
  }, [messages.length]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!onSend || !text.trim()) return;
    setSending(true);
    setError(null);
    try {
      await onSend(text.trim());
      setText('');
    } catch (err) {
      const message = err && typeof err === 'object' && 'message' in err ? String(err.message) : '';
      setError(message || 'Meddelandet kunde inte skickas. Försök igen.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-4">
      {messages.length === 0 ? (
        <p className="text-sm text-gray-500 italic">{emptyText}</p>
      ) : (
        <ol ref={listRef} className="space-y-3 max-h-96 overflow-y-auto pr-1" aria-live="polite">
          {messages.map((m) => (
            <li key={m.id} className={`flex ${m.mine ? 'justify-end' : 'justify-start'}`}>
              <div
                className={`max-w-[85%] rounded-lg px-4 py-2 ${
                  variant === 'notes'
                    ? 'bg-amber-50 border border-amber-200 text-gray-900'
                    : m.mine
                      ? 'bg-brand-600 text-white'
                      : 'bg-gray-100 text-gray-900'
                }`}
              >
                <p className="text-sm whitespace-pre-wrap break-words">{m.message}</p>
                <p
                  className={`mt-1 text-xs ${
                    m.mine && variant === 'conversation' ? 'text-brand-100' : 'text-gray-500'
                  }`}
                >
                  {m.authorName} · {formatTime(m.createdAt)}
                </p>
              </div>
            </li>
          ))}
        </ol>
      )}

      {onSend ? (
        <form onSubmit={handleSubmit} className="space-y-2">
          <label htmlFor={inputId} className="sr-only">
            {placeholder}
          </label>
          <textarea
            id={inputId}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              // Ctrl/Cmd+Enter sends
              if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                e.currentTarget.form?.requestSubmit();
              }
            }}
            rows={3}
            maxLength={5000}
            placeholder={placeholder}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
          />
          {error && (
            <p className="text-sm text-red-600" role="alert">
              {error}
            </p>
          )}
          <div className="flex justify-end">
            <button
              type="submit"
              disabled={sending || !text.trim()}
              className="px-4 py-2 bg-brand-600 text-white text-sm font-medium rounded-lg hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {sending ? 'Skickar…' : sendLabel}
            </button>
          </div>
        </form>
      ) : (
        disabledText && <p className="text-sm text-gray-500">{disabledText}</p>
      )}
    </div>
  );
}
