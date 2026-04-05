'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';
import { getTestUsers, TestUser } from '@/lib/auth';

export default function LoginPage() {
  const router = useRouter();
  const { login, isAuthenticated, isLoading: authLoading } = useAuth();
  const [testUsers, setTestUsers] = useState<TestUser[]>([]);
  const [selectedEmail, setSelectedEmail] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showDevLogin, setShowDevLogin] = useState(true);

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated) {
      router.push('/');
    }
  }, [isAuthenticated, router]);

  // Load test users
  useEffect(() => {
    getTestUsers()
      .then(setTestUsers)
      .catch(() => setTestUsers([]));
  }, []);

  const handleDevLogin = async (email: string) => {
    setSelectedEmail(email);
    setIsLoading(true);
    setError(null);

    try {
      await login({ email, password: 'dev' });
      router.push('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Inloggning misslyckades');
    } finally {
      setIsLoading(false);
      setSelectedEmail(null);
    }
  };

  if (authLoading) {
    return (
      <main className="min-h-screen bg-gradient-to-b from-blue-50 to-white flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-gradient-to-b from-blue-50 to-white flex flex-col">
      <header className="border-b bg-white/80 backdrop-blur-sm">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <Link href="/" className="flex items-center gap-2">
            <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold">E</span>
            </div>
            <span className="font-semibold text-xl">e-Plattform</span>
          </Link>
        </div>
      </header>

      <div className="flex-1 flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">
          <div className="bg-white rounded-2xl shadow-xl border p-8">
            <div className="text-center mb-8">
              <h1 className="text-2xl font-bold text-gray-900 mb-2">Logga in</h1>
              <p className="text-gray-600">
                Välj hur du vill identifiera dig för att komma åt dina ärenden.
              </p>
            </div>

            {error && (
              <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-800">
                {error}
              </div>
            )}

            {/* Development login section */}
            {showDevLogin && testUsers.length > 0 && (
              <div className="mb-6">
                <div className="flex items-center gap-2 mb-4">
                  <div className="flex-1 h-px bg-gray-200"></div>
                  <span className="text-xs text-orange-600 bg-orange-50 px-2 py-1 rounded">
                    Utvecklingsläge
                  </span>
                  <div className="flex-1 h-px bg-gray-200"></div>
                </div>

                <div className="space-y-2">
                  {testUsers.map((user) => (
                    <button
                      key={user.email}
                      onClick={() => handleDevLogin(user.email)}
                      disabled={isLoading}
                      className="w-full flex items-center justify-between p-3 border rounded-lg hover:border-orange-300 hover:bg-orange-50 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-orange-100 rounded-full flex items-center justify-center">
                          <span className="text-orange-600 font-medium">
                            {user.name.charAt(0)}
                          </span>
                        </div>
                        <div className="text-left">
                          <p className="font-medium text-gray-900 text-sm">{user.name}</p>
                          <p className="text-xs text-gray-500">{user.roles.join(', ')}</p>
                        </div>
                      </div>
                      {selectedEmail === user.email && isLoading ? (
                        <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-orange-600"></div>
                      ) : (
                        <svg
                          className="w-4 h-4 text-gray-400"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M9 5l7 7-7 7"
                          />
                        </svg>
                      )}
                    </button>
                  ))}
                </div>

                <div className="flex items-center gap-2 mt-4 mb-4">
                  <div className="flex-1 h-px bg-gray-200"></div>
                  <span className="text-xs text-gray-400">eller</span>
                  <div className="flex-1 h-px bg-gray-200"></div>
                </div>
              </div>
            )}

            {/* Production login methods */}
            <div className="space-y-4">
              <button
                disabled={isLoading}
                className="w-full flex items-center justify-between p-4 border-2 rounded-xl hover:border-blue-300 hover:bg-blue-50 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-[#235971] rounded-lg flex items-center justify-center">
                    <svg className="w-8 h-8 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                    </svg>
                  </div>
                  <div className="text-left">
                    <p className="font-semibold text-gray-900">BankID</p>
                    <p className="text-sm text-gray-500">Mobilt eller på fil</p>
                  </div>
                </div>
                <span className="text-xs text-gray-400 bg-gray-100 px-2 py-1 rounded">
                  Kommer snart
                </span>
              </button>

              <button
                disabled={isLoading}
                className="w-full flex items-center justify-between p-4 border-2 rounded-xl hover:border-green-300 hover:bg-green-50 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-[#00A76F] rounded-lg flex items-center justify-center">
                    <svg className="w-8 h-8 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>
                    </svg>
                  </div>
                  <div className="text-left">
                    <p className="font-semibold text-gray-900">Freja eID</p>
                    <p className="text-sm text-gray-500">Kostnadsfri e-legitimation</p>
                  </div>
                </div>
                <span className="text-xs text-gray-400 bg-gray-100 px-2 py-1 rounded">
                  Kommer snart
                </span>
              </button>
            </div>

            <div className="mt-8 pt-6 border-t text-center">
              <p className="text-sm text-gray-500">
                Har du ingen e-legitimation?{' '}
                <a
                  href="https://www.bankid.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-600 hover:underline"
                >
                  Läs mer om BankID
                </a>
              </p>
            </div>
          </div>

          <p className="text-center text-sm text-gray-500 mt-6">
            Genom att logga in godkänner du våra{' '}
            <Link href="/terms" className="text-blue-600 hover:underline">
              användarvillkor
            </Link>{' '}
            och{' '}
            <Link href="/privacy" className="text-blue-600 hover:underline">
              integritetspolicy
            </Link>
            .
          </p>
        </div>
      </div>
    </main>
  );
}
