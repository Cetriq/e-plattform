/**
 * Base URL of the backend API. Set NEXT_PUBLIC_API_URL per environment
 * (e.g. in Vercel project settings); defaults to the local backend.
 */
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080').replace(/\/$/, '');
