'use client';

import { useEffect, useId, useRef } from 'react';

interface ModalProps {
  /** Called on Escape. */
  onClose: () => void;
  /** "alertdialog" for urgent messages that need an answer. */
  role?: 'dialog' | 'alertdialog';
  /** Classes for the dialog panel (size, padding, rounding). */
  className?: string;
  children: React.ReactNode;
}

/**
 * Modal dialog on the native <dialog> element. showModal() makes the rest of
 * the page inert, keeps focus inside the dialog and closes on Escape; focus
 * goes back to where it was when the dialog closes (WCAG 2.1.2, 2.4.3).
 *
 * The first heading inside the dialog is used as its accessible name.
 */
export function Modal({ onClose, role, className = '', children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const previous = document.activeElement as HTMLElement | null;

    // Name the dialog after its heading
    const heading = dialog.querySelector('h1, h2, h3');
    if (heading) {
      if (!heading.id) heading.id = titleId;
      dialog.setAttribute('aria-labelledby', heading.id);
    }

    dialog.showModal();

    const onCancel = (e: Event) => {
      e.preventDefault();
      onCloseRef.current();
    };
    dialog.addEventListener('cancel', onCancel);
    return () => {
      dialog.removeEventListener('cancel', onCancel);
      if (dialog.open) dialog.close();
      previous?.focus?.();
    };
  }, [titleId]);

  return (
    <dialog
      ref={ref}
      role={role}
      className={`bg-white rounded-xl shadow-xl w-full max-w-md p-0 backdrop:bg-black/50 ${className}`}
    >
      {children}
    </dialog>
  );
}
