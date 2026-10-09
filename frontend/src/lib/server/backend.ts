import 'server-only';

/**
 * Server-side calls from Next.js route handlers to the backend.
 *
 * On Vercel the backend service is reached through the private binding
 * BACKEND_INTERNAL_URL; locally it falls back to the backend's own address.
 */
function backendBaseUrl(): string {
  const internal = process.env.BACKEND_INTERNAL_URL;
  if (internal) return internal;
  const publicUrl = process.env.NEXT_PUBLIC_API_URL;
  if (publicUrl && /^https?:\/\//.test(publicUrl)) return publicUrl;
  return 'http://localhost:8080';
}

/**
 * Call the backend on behalf of the user, passing on their Authorization
 * header so the backend makes the access decision.
 */
export function backendFetch(path: string, authorization: string | null, init: RequestInit = {}) {
  const headers = new Headers(init.headers);
  if (authorization) headers.set('Authorization', authorization);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  return fetch(new URL(path, backendBaseUrl()), { ...init, headers, cache: 'no-store' });
}

export async function backendError(response: Response): Promise<string> {
  try {
    const body = await response.json();
    return body.error || body.message || response.statusText;
  } catch {
    return response.statusText;
  }
}
