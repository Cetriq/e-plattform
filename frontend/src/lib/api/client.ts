import { clearAuthStorage, getStoredToken } from '@/lib/auth';
import { API_BASE_URL } from '@/lib/config';

export interface ApiError {
  message: string;
  status: number;
}

function getHeaders(): HeadersInit {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  const token = getStoredToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

/**
 * A 401 on a request that carried a token means the session has expired or
 * the user no longer exists. Clear it and send the user to the login page,
 * bringing them back here afterwards.
 */
export function handleUnauthorized(status: number): void {
  if (status !== 401 || typeof window === 'undefined' || !getStoredToken()) return;
  clearAuthStorage();
  const here = window.location.pathname + window.location.search;
  window.location.href = `/auth/login?redirect=${encodeURIComponent(here)}`;
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    handleUnauthorized(response.status);
    const error: ApiError = {
      message: response.statusText,
      status: response.status,
    };
    try {
      const body = await response.json();
      error.message = body.message || body.error || response.statusText;
    } catch {
      // Ignore JSON parse errors
    }
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const api = {
  get: async <T>(path: string): Promise<T> => {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      headers: getHeaders(),
    });
    return handleResponse<T>(response);
  },

  post: async <T>(path: string, body?: unknown): Promise<T> => {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: getHeaders(),
      body: body ? JSON.stringify(body) : undefined,
    });
    return handleResponse<T>(response);
  },

  put: async <T>(path: string, body?: unknown): Promise<T> => {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: body ? JSON.stringify(body) : undefined,
    });
    return handleResponse<T>(response);
  },

  patch: async <T>(path: string, body?: unknown): Promise<T> => {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'PATCH',
      headers: getHeaders(),
      body: body ? JSON.stringify(body) : undefined,
    });
    return handleResponse<T>(response);
  },

  delete: async <T>(path: string): Promise<T> => {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    return handleResponse<T>(response);
  },

  /**
   * Fetch a file with the user's token. Plain links and <img> tags can't send
   * the Authorization header, so protected files have to be fetched this way.
   */
  getBlob: async (path: string): Promise<Blob> => {
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const response = await fetch(`${API_BASE_URL}${path}`, { headers });
    if (!response.ok) {
      handleUnauthorized(response.status);
      const error: ApiError = {
        message: response.statusText,
        status: response.status,
      };
      throw error;
    }
    return response.blob();
  },

  downloadBlob: async (path: string, filename: string): Promise<void> => {
    const blob = await api.getBlob(path);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  },
};
