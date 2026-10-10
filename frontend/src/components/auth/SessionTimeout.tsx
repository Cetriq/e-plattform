'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { Modal } from '@/components/ui/Modal';

/** How long before the session ends the warning is shown. */
const WARN_BEFORE_MS = 3 * 60 * 1000;

function expiresAt(token: string | null): number | null {
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

/**
 * Warns before the login session runs out and lets the user extend it
 * (WCAG 2.2.1). Answers in a form are saved as you go, so nothing is lost
 * if the session does end.
 */
export function SessionTimeout() {
  const { token, isAuthenticated, extendSession, logout } = useAuth();
  const [warning, setWarning] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const end = isAuthenticated ? expiresAt(token) : null;

  useEffect(() => {
    if (!end) return;
    const warnIn = end - Date.now() - WARN_BEFORE_MS;
    const warnTimer = setTimeout(() => setWarning(true), Math.max(warnIn, 0));
    const endTimer = setTimeout(async () => {
      setWarning(false);
      await logout();
      window.location.href = '/auth/login?utloggad=1';
    }, Math.max(end - Date.now(), 0));
    return () => {
      clearTimeout(warnTimer);
      clearTimeout(endTimer);
    };
  }, [end, logout]);

  useEffect(() => {
    if (!warning || !end) return;
    const tick = () => setSecondsLeft(Math.max(0, Math.round((end - Date.now()) / 1000)));
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [warning, end]);

  if (!warning) return null;

  const minutes = Math.floor(secondsLeft / 60);
  const seconds = secondsLeft % 60;
  const remaining = minutes > 0 ? `${minutes} min ${seconds} s` : `${seconds} s`;

  return (
    <Modal role="alertdialog" onClose={() => setWarning(false)} className="p-6">
      <h2 className="text-lg font-semibold text-gray-900">Du loggas snart ut</h2>
      <p className="mt-2 text-gray-700">
        Av säkerhetsskäl avslutas inloggningen om{' '}
        <span className="font-medium tabular-nums">{remaining}</span>. Det du har fyllt i är sparat.
      </p>
      <div className="mt-6 flex flex-wrap gap-2">
        <button
          type="button"
          autoFocus
          className="btn-primary"
          onClick={async () => {
            if (await extendSession()) setWarning(false);
          }}
        >
          Fortsätt vara inloggad
        </button>
        <button type="button" className="btn-secondary" onClick={() => logout()}>
          Logga ut
        </button>
      </div>
    </Modal>
  );
}
