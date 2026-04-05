export interface User {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string;
  roles: string[];
  permissions: string[];
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

export interface LoginCredentials {
  email: string;
  password?: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface TestUser {
  email: string;
  name: string;
  roles: string[];
}
