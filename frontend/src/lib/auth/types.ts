export interface User {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string;
  phone: string | null;
  /** Global roles plus the roles in the active organisation. */
  roles: string[];
  permissions: string[];
  /** The organisation staff are working for; absent for citizens. */
  activeOrganization?: OrganizationRef | null;
  /** Organisations the user can switch to. */
  memberships?: OrganizationRef[];
  /** Highest confidentiality (NORMAL, RESTRICTED, CONFIDENTIAL) the user may see in the active organisation. */
  clearance?: string | null;
}

export interface OrganizationRef {
  id: string;
  name: string;
  slug: string;
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
