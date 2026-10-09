'use client';

import { useState, useCallback, useRef, useEffect } from 'react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: string;
  type: ToastType;
  title: string;
  description?: string;
  duration?: number;
}

interface ToastState {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, 'id'>) => void;
  removeToast: (id: string) => void;
  clearToasts: () => void;
}

// Global state for toasts (allows usage outside of React components)
let globalToasts: Toast[] = [];
let listeners: ((toasts: Toast[]) => void)[] = [];

function notifyListeners() {
  listeners.forEach((listener) => listener([...globalToasts]));
}

function generateId(): string {
  return `toast-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

// Global toast functions that can be imported anywhere
export function toast(options: Omit<Toast, 'id'>): string {
  const id = generateId();
  const newToast: Toast = {
    ...options,
    id,
    duration: options.duration ?? 5000,
  };
  globalToasts = [...globalToasts, newToast];
  notifyListeners();
  return id;
}

export function dismissToast(id: string): void {
  globalToasts = globalToasts.filter((t) => t.id !== id);
  notifyListeners();
}

export function clearAllToasts(): void {
  globalToasts = [];
  notifyListeners();
}

// Convenience methods
toast.success = (title: string, description?: string) =>
  toast({ type: 'success', title, description });

toast.error = (title: string, description?: string) =>
  toast({ type: 'error', title, description, duration: 8000 });

toast.warning = (title: string, description?: string) =>
  toast({ type: 'warning', title, description });

toast.info = (title: string, description?: string) =>
  toast({ type: 'info', title, description });

// Hook for components that need to display toasts
export function useToast(): ToastState {
  const [toasts, setToasts] = useState<Toast[]>(globalToasts);

  useEffect(() => {
    const listener = (newToasts: Toast[]) => {
      setToasts(newToasts);
    };
    listeners.push(listener);
    return () => {
      listeners = listeners.filter((l) => l !== listener);
    };
  }, []);

  const addToast = useCallback((toastOptions: Omit<Toast, 'id'>) => {
    toast(toastOptions);
  }, []);

  const removeToast = useCallback((id: string) => {
    dismissToast(id);
  }, []);

  const clearToasts = useCallback(() => {
    clearAllToasts();
  }, []);

  return { toasts, addToast, removeToast, clearToasts };
}
