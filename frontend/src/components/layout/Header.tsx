'use client';

import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useState, useRef, useEffect } from 'react';

export function Header() {
  const { user, isAuthenticated, isLoading, logout, hasRole } = useAuth();
  const [showUserMenu, setShowUserMenu] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);

  // Close the menu on click outside, on Escape (focus back to the button)
  // and when keyboard focus leaves it
  useEffect(() => {
    if (!showUserMenu) return;
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setShowUserMenu(false);
      }
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setShowUserMenu(false);
        menuButtonRef.current?.focus();
      }
    }
    function handleFocus(event: FocusEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setShowUserMenu(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKey);
    document.addEventListener('focusin', handleFocus);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKey);
      document.removeEventListener('focusin', handleFocus);
    };
  }, [showUserMenu]);

  const handleLogout = async () => {
    await logout();
    setShowUserMenu(false);
  };

  return (
    <header className="border-b bg-white/80 backdrop-blur-sm sticky top-0 z-50">
      <div className="container mx-auto px-4 py-4 flex flex-wrap items-center justify-between gap-x-6 gap-y-3">
        <Link href="/" className="flex items-center gap-2">
          <div className="w-8 h-8 bg-brand-600 rounded-lg flex items-center justify-center">
            <span className="text-white font-bold">E</span>
          </div>
          <span className="font-semibold text-xl">e-Plattform</span>
        </Link>

        <nav className="flex flex-wrap items-center gap-x-6 gap-y-2">
          <Link href="/citizen/services" className="text-gray-600 hover:text-gray-900">
            Tjänster
          </Link>
          <Link href="/citizen/cases" className="text-gray-600 hover:text-gray-900">
            Mina ärenden
          </Link>

          {/* Show manager/admin links if user has those roles */}
          {isAuthenticated && hasRole('MANAGER') && (
            <Link href="/manager/dashboard" className="hidden md:inline text-gray-600 hover:text-gray-900">
              Handläggare
            </Link>
          )}
          {isAuthenticated && hasRole('ADMIN') && (
            <Link href="/admin" className="hidden md:inline text-gray-600 hover:text-gray-900">
              Admin
            </Link>
          )}
          {isAuthenticated && hasRole('SECURITY_OFFICER') && (
            <Link href="/security" className="hidden md:inline text-gray-600 hover:text-gray-900">
              Informationssäkerhet
            </Link>
          )}
          {isAuthenticated && hasRole('OPERATIONS') && (
            <Link href="/ops" className="hidden md:inline text-gray-600 hover:text-gray-900">
              IT & drift
            </Link>
          )}

          {isLoading ? (
            <div className="w-8 h-8 rounded-full bg-gray-200 animate-pulse"></div>
          ) : isAuthenticated && user ? (
            <div className="relative" ref={menuRef}>
              <button
                ref={menuButtonRef}
                type="button"
                aria-expanded={showUserMenu}
                aria-controls="user-menu"
                onClick={() => setShowUserMenu(!showUserMenu)}
                className="flex items-center gap-2 hover:bg-gray-100 rounded-lg px-3 py-2 transition-colors"
              >
                <div aria-hidden="true" className="w-8 h-8 bg-brand-100 rounded-full flex items-center justify-center">
                  <span className="text-brand-600 font-medium text-sm">
                    {user.displayName?.charAt(0) || user.email.charAt(0).toUpperCase()}
                  </span>
                </div>
                <span className="text-gray-700 font-medium sr-only sm:not-sr-only">
                  {user.displayName || user.email}
                </span>
                <svg
                  aria-hidden="true"
                  className={`w-4 h-4 text-gray-500 transition-transform ${showUserMenu ? 'rotate-180' : ''}`}
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>

              {showUserMenu && (
                <div id="user-menu" className="absolute right-0 mt-2 w-64 bg-white rounded-lg shadow-lg border py-2 z-50">
                  <div className="px-4 py-3 border-b">
                    <p className="font-medium text-gray-900">{user.displayName}</p>
                    <p className="text-sm text-gray-500">{user.email}</p>
                    <div className="flex flex-wrap gap-1 mt-2">
                      {user.roles.map((role) => (
                        <span
                          key={role}
                          className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded"
                        >
                          {role}
                        </span>
                      ))}
                    </div>
                  </div>

                  <Link
                    href="/citizen/cases"
                    className="block px-4 py-2 text-gray-700 hover:bg-gray-100"
                    onClick={() => setShowUserMenu(false)}
                  >
                    Mina ärenden
                  </Link>

                  <Link
                    href="/citizen/profile"
                    className="block px-4 py-2 text-gray-700 hover:bg-gray-100"
                    onClick={() => setShowUserMenu(false)}
                  >
                    Min profil
                  </Link>

                  {hasRole('MANAGER') && (
                    <Link
                      href="/manager/dashboard"
                      className="block px-4 py-2 text-gray-700 hover:bg-gray-100"
                      onClick={() => setShowUserMenu(false)}
                    >
                      Handläggarportal
                    </Link>
                  )}

                  {hasRole('ADMIN') && (
                    <Link
                      href="/admin"
                      className="block px-4 py-2 text-gray-700 hover:bg-gray-100"
                      onClick={() => setShowUserMenu(false)}
                    >
                      Administration
                    </Link>
                  )}

                  {hasRole('SECURITY_OFFICER') && (
                    <Link
                      href="/security"
                      className="block px-4 py-2 text-gray-700 hover:bg-gray-100"
                      onClick={() => setShowUserMenu(false)}
                    >
                      Informationssäkerhet & dataskydd
                    </Link>
                  )}

                  {hasRole('OPERATIONS') && (
                    <Link
                      href="/ops"
                      className="block px-4 py-2 text-gray-700 hover:bg-gray-100"
                      onClick={() => setShowUserMenu(false)}
                    >
                      IT & drift
                    </Link>
                  )}

                  <div className="border-t mt-2 pt-2">
                    <button
                      onClick={handleLogout}
                      className="w-full text-left px-4 py-2 text-red-700 hover:bg-red-50"
                    >
                      Logga ut
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <Link
              href="/auth/login"
              className="bg-brand-600 text-white px-4 py-2 rounded-lg hover:bg-brand-700 transition-colors"
            >
              Logga in
            </Link>
          )}
        </nav>
      </div>
    </header>
  );
}
