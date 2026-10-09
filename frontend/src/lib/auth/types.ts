export interface User {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string;
  phone: string | null;
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
  /** Shared demo access code, when the demo requires one. */
  accessCode?: string;
}

export interface LoginConfig {
  /** Visitors must enter a shared access code before logging in. */
  accessCodeRequired: boolean;
  /** Citizens get a new, isolated demo account instead of a shared persona. */
  isolatedCitizens: boolean;
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
