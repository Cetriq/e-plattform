'use client';

import React, { useEffect, useState } from 'react';
import * as ToastPrimitives from '@radix-ui/react-toast';
import { useToast, type Toast as ToastType, dismissToast } from '@/hooks/useToast';

const toastStyles: Record<ToastType['type'], { bg: string; border: string; icon: string; iconColor: string }> = {
  success: {
    bg: 'bg-green-50',
    border: 'border-green-200',
    icon: 'M5 13l4 4L19 7',
    iconColor: 'text-green-600',
  },
  error: {
    bg: 'bg-red-50',
    border: 'border-red-200',
    icon: 'M6 18L18 6M6 6l12 12',
    iconColor: 'text-red-600',
  },
  warning: {
    bg: 'bg-yellow-50',
    border: 'border-yellow-200',
    icon: 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z',
    iconColor: 'text-yellow-600',
  },
  info: {
    bg: 'bg-blue-50',
    border: 'border-blue-200',
    icon: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
    iconColor: 'text-blue-600',
  },
};

interface ToastItemProps {
  toast: ToastType;
  onClose: (id: string) => void;
}

function ToastItem({ toast, onClose }: ToastItemProps) {
  const [open, setOpen] = useState(true);
  const style = toastStyles[toast.type];

  useEffect(() => {
    if (toast.duration && toast.duration > 0) {
      const timer = setTimeout(() => {
        setOpen(false);
      }, toast.duration);
      return () => clearTimeout(timer);
    }
  }, [toast.duration]);

  return (
    <ToastPrimitives.Root
      open={open}
      onOpenChange={(isOpen) => {
        setOpen(isOpen);
        if (!isOpen) {
          // Small delay to allow animation
          setTimeout(() => onClose(toast.id), 100);
        }
      }}
      className={`
        ${style.bg} ${style.border}
        border rounded-lg shadow-lg p-4
        flex items-start gap-3
        data-[state=open]:animate-in data-[state=closed]:animate-out
        data-[swipe=end]:animate-out
        data-[state=closed]:fade-out-80 data-[state=open]:fade-in-0
        data-[state=closed]:slide-out-to-right-full data-[state=open]:slide-in-from-top-full
        data-[swipe=cancel]:translate-x-0 data-[swipe=end]:translate-x-[var(--radix-toast-swipe-end-x)]
        data-[swipe=move]:translate-x-[var(--radix-toast-swipe-move-x)] data-[swipe=move]:transition-none
      `}
      aria-live="polite"
      aria-atomic="true"
    >
      <div className={`flex-shrink-0 ${style.iconColor}`}>
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={style.icon} />
        </svg>
      </div>

      <div className="flex-1 min-w-0">
        <ToastPrimitives.Title className="text-sm font-medium text-gray-900">
          {toast.title}
        </ToastPrimitives.Title>
        {toast.description && (
          <ToastPrimitives.Description className="text-sm text-gray-600 mt-1">
            {toast.description}
          </ToastPrimitives.Description>
        )}
      </div>

      <ToastPrimitives.Close
        className="flex-shrink-0 text-gray-400 hover:text-gray-600 transition-colors"
        aria-label="Stäng"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </ToastPrimitives.Close>
    </ToastPrimitives.Root>
  );
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const { toasts, removeToast } = useToast();

  return (
    <ToastPrimitives.Provider swipeDirection="right">
      {children}

      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} onClose={removeToast} />
      ))}

      <ToastPrimitives.Viewport
        className="fixed top-4 right-4 flex flex-col gap-2 w-full max-w-sm z-[100] outline-none"
        aria-label="Notifikationer"
      />
    </ToastPrimitives.Provider>
  );
}

export { ToastItem };
