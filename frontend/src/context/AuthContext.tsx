'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import {
  User,
  AuthResponse,
  AuthState,
  LoginCredentials,
  login as apiLogin,
  createDemoCitizen,
  logout as apiLogout,
  getCurrentUser,
  refreshSession,
  getStoredToken,
  setStoredToken,
  setStoredUser,
  clearAuthStorage,
} from '@/lib/auth';

interface AuthContextType extends AuthState {
  login: (credentials: LoginCredentials) => Promise<void>;
  loginAsDemoCitizen: (accessCode?: string) => Promise<void>;
  logout: () => Promise<void>;
  updateUser: (user: User) => void;
  /** Swap the token for a fresh one; false if the session already ended. */
  extendSession: () => Promise<boolean>;
  hasRole: (role: string) => boolean;
  hasPermission: (permission: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: true,
  });

  // Initialize auth state from storage
  useEffect(() => {
    const initAuth = async () => {
      const storedToken = getStoredToken();

      if (storedToken) {
        try {
          const user = await getCurrentUser(storedToken);
          setState({
            user,
            token: storedToken,
            isAuthenticated: true,
            isLoading: false,
          });
        } catch (error) {
          // Token is invalid, clear storage
          clearAuthStorage();
          setState({
            user: null,
            token: null,
            isAuthenticated: false,
            isLoading: false,
          });
        }
      } else {
        setState(prev => ({ ...prev, isLoading: false }));
      }
    };

    initAuth();
  }, []);

  const startSession = useCallback(async (authenticate: () => Promise<AuthResponse>) => {
    setState(prev => ({ ...prev, isLoading: true }));

    try {
      const response = await authenticate();

      // Store token and user
      setStoredToken(response.token);
      setStoredUser(response.user);

      setState({
        user: response.user,
        token: response.token,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (error) {
      setState(prev => ({ ...prev, isLoading: false }));
      throw error;
    }
  }, []);

  const login = useCallback(
    (credentials: LoginCredentials) => startSession(() => apiLogin(credentials)),
    [startSession]
  );

  const loginAsDemoCitizen = useCallback(
    (accessCode?: string) => startSession(() => createDemoCitizen(accessCode)),
    [startSession]
  );

  const logout = useCallback(async () => {
    if (state.token) {
      try {
        await apiLogout(state.token);
      } catch (error) {
        // Ignore logout errors
      }
    }

    clearAuthStorage();
    setState({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false,
    });
  }, [state.token]);

  const updateUser = useCallback((user: User) => {
    setStoredUser(user);
    setState(prev => ({ ...prev, user }));
  }, []);

  const extendSession = useCallback(async () => {
    const token = getStoredToken();
    if (!token) return false;
    try {
      const response = await refreshSession(token);
      setStoredToken(response.token);
      setStoredUser(response.user);
      setState(prev => ({ ...prev, token: response.token, user: response.user }));
      return true;
    } catch {
      return false;
    }
  }, []);

  const hasRole = useCallback((role: string) => {
    return state.user?.roles.includes(role) ?? false;
  }, [state.user]);

  const hasPermission = useCallback((permission: string) => {
    if (!state.user) return false;

    return state.user.permissions.some(p =>
      p === '*' ||
      p === permission ||
      (p.endsWith(':*') && permission.startsWith(p.replace(':*', ':')))
    );
  }, [state.user]);

  return (
    <AuthContext.Provider
      value={{
        ...state,
        login,
        loginAsDemoCitizen,
        logout,
        updateUser,
        extendSession,
        hasRole,
        hasPermission,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

// Hook for requiring authentication
export function useRequireAuth(redirectTo: string = '/auth/login') {
  const auth = useAuth();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated) {
      window.location.href = redirectTo;
    }
  }, [auth.isLoading, auth.isAuthenticated, redirectTo]);

  return auth;
}

// Hook for requiring specific role
export function useRequireRole(role: string, redirectTo: string = '/') {
  const auth = useRequireAuth();

  useEffect(() => {
    if (!auth.isLoading && auth.isAuthenticated && !auth.hasRole(role)) {
      window.location.href = redirectTo;
    }
  }, [auth.isLoading, auth.isAuthenticated, auth.hasRole, role, redirectTo]);

  return auth;
}
