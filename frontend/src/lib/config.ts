/**
 * Base URL of the backend API, set with NEXT_PUBLIC_API_URL per environment.
 * Use "/" when the API is served from the same domain (Vercel Services);
 * defaults to the local backend.
 */
const configuredApiUrl = process.env.NEXT_PUBLIC_API_URL;
export const API_BASE_URL = (configuredApiUrl === undefined || configuredApiUrl === ''
  ? 'http://localhost:8080'
  : configuredApiUrl
).replace(/\/$/, '');

/**
 * Where attachments are stored:
 * - "backend": uploaded to and downloaded from the backend (MinIO, local Docker)
 * - "blob": uploaded by the browser straight to a private Vercel Blob store
 */
export const FILE_STORAGE: 'backend' | 'blob' =
  process.env.NEXT_PUBLIC_FILE_STORAGE === 'blob' ? 'blob' : 'backend';
