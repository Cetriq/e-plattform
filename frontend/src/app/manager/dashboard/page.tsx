'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/context/AuthContext';
import { getSubmittedCases, searchCases, type PaginatedResponse } from '@/lib/api/manager';
import type { CaseSummary } from '@/lib/api/cases';

const statusColors: Record<string, { bg: string; text: string }> = {
  DRAFT: { bg: 'bg-gray-100', text: 'text-gray-700' },
  SUBMITTED: { bg: 'bg-blue-100', text: 'text-blue-700' },
  IN_PROGRESS: { bg: 'bg-yellow-100', text: 'text-yellow-700' },
  WAITING_FOR_USER: { bg: 'bg-orange-100', text: 'text-orange-700' },
  WAITING_FOR_EXTERNAL: { bg: 'bg-purple-100', text: 'text-purple-700' },
  COMPLETED: { bg: 'bg-green-100', text: 'text-green-700' },
  CANCELLED: { bg: 'bg-red-100', text: 'text-red-700' },
  ARCHIVED: { bg: 'bg-gray-100', text: 'text-gray-500' },
};

const priorityLabels: Record<string, { label: string; color: string }> = {
  LOW: { label: 'Låg', color: 'text-gray-500' },
  NORMAL: { label: 'Normal', color: 'text-gray-700' },
  HIGH: { label: 'Hög', color: 'text-orange-600' },
  URGENT: { label: 'Brådskande', color: 'text-red-600' },
};

export default function ManagerDashboardPage() {
  const { isAuthenticated, isLoading: authLoading, user } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [isSearching, setIsSearching] = useState(false);

  const { data: casesResponse, isLoading, error, refetch } = useQuery({
    queryKey: ['manager-cases', currentPage, isSearching ? searchQuery : ''],
    queryFn: async () => {
      if (isSearching && searchQuery.trim()) {
        return searchCases(searchQuery, currentPage);
      }
      return getSubmittedCases(currentPage);
    },
    enabled: isAuthenticated,
  });

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSearching(true);
    setCurrentPage(0);
    refetch();
  };

  const clearSearch = () => {
    setSearchQuery('');
    setIsSearching(false);
    setCurrentPage(0);
    refetch();
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('sv-SE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const formatRelativeDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return 'Idag';
    if (diffDays === 1) return 'Igår';
    if (diffDays < 7) return `${diffDays} dagar sedan`;
    return formatDate(dateString);
  };

  // Redirect to login if not authenticated
  if (!authLoading && !isAuthenticated) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="bg-white p-8 rounded-lg shadow-md text-center max-w-md">
          <svg
            className="w-12 h-12 text-blue-500 mx-auto mb-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
            />
          </svg>
          <h2 className="text-xl font-bold mb-2">Inloggning krävs</h2>
          <p className="text-gray-600 mb-4">
            Du måste vara inloggad som handläggare för att komma åt denna sida.
          </p>
          <Link
            href="/auth/login?redirect=/manager/dashboard"
            className="inline-flex items-center justify-center bg-blue-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-blue-700"
          >
            Logga in
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Manager Header */}
      <header className="bg-white border-b shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center gap-4">
              <Link href="/manager/dashboard" className="flex items-center gap-2">
                <div className="w-8 h-8 bg-blue-600 rounded flex items-center justify-center">
                  <span className="text-white font-bold text-sm">E</span>
                </div>
                <span className="font-semibold text-gray-900">Handläggare</span>
              </Link>
              <nav className="hidden md:flex gap-6 ml-8">
                <Link
                  href="/manager/dashboard"
                  className="text-blue-600 font-medium border-b-2 border-blue-600 pb-4 -mb-4"
                >
                  Ärenden
                </Link>
                <Link
                  href="/manager/flows"
                  className="text-gray-600 hover:text-gray-900"
                >
                  E-tjänster
                </Link>
              </nav>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">{user?.displayName}</span>
              <Link
                href="/"
                className="text-sm text-gray-500 hover:text-gray-700"
              >
                Till medborgarsidan
              </Link>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Page header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Inkomna ärenden</h1>
          <p className="text-gray-600 mt-1">
            Hantera och granska inskickade ärenden.
          </p>
        </div>

        {/* Stats cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
          <div className="bg-white rounded-lg shadow-sm p-4 border">
            <div className="text-sm text-gray-500">Totalt</div>
            <div className="text-2xl font-bold text-gray-900">
              {casesResponse?.totalElements ?? '-'}
            </div>
          </div>
          <div className="bg-white rounded-lg shadow-sm p-4 border">
            <div className="text-sm text-gray-500">Nya idag</div>
            <div className="text-2xl font-bold text-blue-600">-</div>
          </div>
          <div className="bg-white rounded-lg shadow-sm p-4 border">
            <div className="text-sm text-gray-500">Under behandling</div>
            <div className="text-2xl font-bold text-yellow-600">-</div>
          </div>
          <div className="bg-white rounded-lg shadow-sm p-4 border">
            <div className="text-sm text-gray-500">Brådskande</div>
            <div className="text-2xl font-bold text-red-600">-</div>
          </div>
        </div>

        {/* Search and filters */}
        <div className="bg-white rounded-lg shadow-sm border p-4 mb-6">
          <form onSubmit={handleSearch} className="flex gap-4">
            <div className="flex-1 relative">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Sök på ärendenummer, namn eller e-tjänst..."
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {isSearching && (
                <button
                  type="button"
                  onClick={clearSearch}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              )}
            </div>
            <button
              type="submit"
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 flex items-center gap-2"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              Sök
            </button>
          </form>
        </div>

        {/* Cases table */}
        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            <span className="ml-3 text-gray-600">Laddar ärenden...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800">
              Kunde inte ladda ärenden. Försök igen senare.
            </p>
          </div>
        ) : casesResponse?.content?.length === 0 ? (
          <div className="bg-white rounded-lg shadow-sm border p-8 text-center">
            <svg
              className="w-12 h-12 text-gray-400 mx-auto mb-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
              />
            </svg>
            <h3 className="text-lg font-medium text-gray-900 mb-2">
              {isSearching ? 'Inga träffar' : 'Inga ärenden'}
            </h3>
            <p className="text-gray-600">
              {isSearching
                ? 'Prova att söka med andra sökord.'
                : 'Det finns inga inskickade ärenden att hantera.'}
            </p>
          </div>
        ) : (
          <>
            <div className="bg-white rounded-lg shadow-sm border overflow-hidden">
              <table className="w-full">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Ärende
                    </th>
                    <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Status
                    </th>
                    <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Prioritet
                    </th>
                    <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Inkom
                    </th>
                    <th className="px-6 py-3"></th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {casesResponse?.content?.map((c: CaseSummary) => {
                    const statusStyle = c.statusColor
                      ? { backgroundColor: `${c.statusColor}20`, color: c.statusColor }
                      : undefined;
                    const defaultStatus = statusColors['SUBMITTED'];
                    const priority = priorityLabels[(c as unknown as { priority?: string }).priority || 'NORMAL'] || priorityLabels.NORMAL;

                    return (
                      <tr key={c.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4">
                          <div>
                            <p className="font-medium text-gray-900">{c.flowName}</p>
                            <p className="text-sm text-gray-500">{c.referenceNumber}</p>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <span
                            className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${!statusStyle ? `${defaultStatus.bg} ${defaultStatus.text}` : ''}`}
                            style={statusStyle}
                          >
                            {c.statusName || 'Inskickad'}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <span className={`text-sm ${priority.color}`}>
                            {priority.label}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-600">
                          {c.submittedAt ? formatRelativeDate(c.submittedAt) : '-'}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <Link
                            href={`/manager/cases/${c.id}`}
                            className="text-blue-600 hover:text-blue-800 font-medium text-sm"
                          >
                            Visa
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {casesResponse && casesResponse.totalPages > 1 && (
              <div className="flex items-center justify-between mt-4">
                <p className="text-sm text-gray-600">
                  Visar {casesResponse.number * casesResponse.size + 1}-
                  {Math.min(
                    (casesResponse.number + 1) * casesResponse.size,
                    casesResponse.totalElements
                  )}{' '}
                  av {casesResponse.totalElements} ärenden
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                    disabled={casesResponse.first}
                    className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
                  >
                    Föregående
                  </button>
                  <button
                    onClick={() => setCurrentPage((p) => p + 1)}
                    disabled={casesResponse.last}
                    className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
                  >
                    Nästa
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
