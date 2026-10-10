'use client';

import { useEffect } from 'react';
import { API_BASE_URL } from '@/lib/config';

/** Vercel stops an idle backend instance after 5 minutes; ping just inside that. */
const INTERVAL_MS = 4 * 60 * 1000;

function ping() {
  // Read the (small) body so the request completes and the connection is released
  fetch(`${API_BASE_URL}/api/v1/public/auth/config`)
    .then((response) => response.text())
    .catch(() => {});
}

/**
 * Wakes the backend as soon as someone opens the site, so its cold start
 * runs while they read the page instead of on their first click, and keeps
 * it warm while a tab is open and visible (a demo paused for a few minutes).
 * Hidden tabs do not ping, so an idle site still scales to zero.
 */
export function BackendKeepWarm() {
  useEffect(() => {
    ping();
    let timer: ReturnType<typeof setInterval> | undefined;
    const start = () => {
      clearInterval(timer);
      timer = setInterval(() => {
        if (document.visibilityState === 'visible') ping();
      }, INTERVAL_MS);
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        ping();
        start();
      }
    };
    start();
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, []);

  return null;
}
