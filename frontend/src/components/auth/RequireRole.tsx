'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';

interface RequireRoleProps {
  /** The user needs at least one of these roles. */
  roles: string[];
  children: React.ReactNode;
}

/**
 * Renders its children only for logged-in users with one of the given roles.
 * Visitors who are not logged in are sent to the login page and brought back
 * afterwards. The backend enforces the same rules; this only avoids showing
 * pages that would fail.
 */
export function RequireRole({ roles, children }: RequireRoleProps) {
  const { isLoading, isAuthenticated, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace(`/auth/login?redirect=${encodeURIComponent(pathname)}`);
    }
  }, [isLoading, isAuthenticated, pathname, router]);

  if (isLoading || !isAuthenticated) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center" role="status">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-600" aria-hidden="true"></div>
        <span className="sr-only">Laddar…</span>
      </div>
    );
  }

  if (!roles.some((role) => user?.roles.includes(role))) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
        <div className="bg-white p-8 rounded-lg shadow-md text-center max-w-md">
          <h1 className="text-xl font-bold mb-2">Saknar behörighet</h1>
          <p className="text-gray-600 mb-6">
            Du är inloggad som {user?.displayName}, som inte har tillgång till den här delen av
            e-Plattformen.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link
              href="/"
              className="inline-flex items-center justify-center px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
            >
              Till startsidan
            </Link>
            <Link
              href={`/auth/login?switch=1&redirect=${encodeURIComponent(pathname)}`}
              className="inline-flex items-center justify-center px-4 py-2 bg-brand-600 text-white rounded-lg hover:bg-brand-700"
            >
              Byt användare
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
