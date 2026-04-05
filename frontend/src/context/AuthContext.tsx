'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import {
  User,
  AuthState,
  LoginCredentials,
  login as apiLogin,
  logout as apiLogout,
  getCurrentUser,
  getStoredToken,
  setStoredToken,
  setStoredUser,
  clearAuthStorage,
} from '@/lib/auth';

interface AuthContextType extends AuthState {
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => Promise<void>;
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

  const login = useCallback(async (credentials: LoginCredentials) => {
    setState(prev => ({ ...prev, isLoading: true }));

    try {
      const response = await apiLogin(credentials);

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
        logout,
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
