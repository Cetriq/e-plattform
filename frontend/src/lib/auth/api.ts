import { AuthResponse, LoginConfig, LoginCredentials, TestUser, User } from './types';
import { API_BASE_URL } from '@/lib/config';

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/public/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(credentials),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Login failed' }));
    throw new Error(error.error || 'Login failed');
  }

  return response.json();
}

export async function createDemoCitizen(accessCode?: string): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/public/auth/demo-citizen`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ accessCode }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Login failed' }));
    throw new Error(error.error || 'Login failed');
  }

  return response.json();
}

export async function getLoginConfig(): Promise<LoginConfig> {
  const response = await fetch(`${API_BASE_URL}/api/v1/public/auth/config`);

  if (!response.ok) {
    throw new Error('Failed to get login configuration');
  }

  return response.json();
}

export async function getCurrentUser(token: string): Promise<User> {
  const response = await fetch(`${API_BASE_URL}/api/v1/public/auth/me`, {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('Failed to get current user');
  }

  return response.json();
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string | null;
}

export async function updateProfile(
  token: string,
  data: UpdateProfileRequest,
): Promise<User> {
  const response = await fetch(`${API_BASE_URL}/api/v1/public/auth/me`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Update failed' }));
    throw new Error(error.error || 'Update failed');
  }

  return response.json();
}

export async function logout(token: string): Promise<void> {
  await fetch(`${API_BASE_URL}/api/v1/public/auth/logout`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });
}

export async function getTestUsers(): Promise<TestUser[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/public/auth/test-users`);

  if (!response.ok) {
    throw new Error('Failed to get test users');
  }

  const data = await response.json();
  return data.users;
}
