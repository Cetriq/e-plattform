import { getStoredToken } from '@/lib/auth';
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

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
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

  downloadBlob: async (path: string, filename: string): Promise<void> => {
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const response = await fetch(`${API_BASE_URL}${path}`, { headers });
    if (!response.ok) {
      const error: ApiError = {
        message: response.statusText,
        status: response.status,
      };
      throw error;
    }
    const blob = await response.blob();
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
