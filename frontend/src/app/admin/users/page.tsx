'use client';

import { useEffect, useMemo, useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { format } from 'date-fns';
import { sv } from 'date-fns/locale';
import { useAuth } from '@/context/AuthContext';

interface UserListItem {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string;
  phone: string | null;
  roles: string[];
  active: boolean;
  lastLoginAt: string | null;
  createdAt: string | null;
}

interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

const ROLE_FILTERS = ['ADMIN', 'FLOW_EDITOR', 'MANAGER', 'USER'];

function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  try {
    return format(new Date(iso), 'yyyy-MM-dd HH:mm', { locale: sv });
  } catch {
    return '—';
  }
}

export default function AdminUsersPage() {
  const { hasRole } = useAuth();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<string>('');
  const [page, setPage] = useState(0);
  const size = 25;
  const debouncedSearch = useDebouncedValue(search, 300);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, role]);

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(size));
    if (debouncedSearch.trim()) params.set('q', debouncedSearch.trim());
    if (role) params.set('role', role);
    return params.toString();
  }, [page, debouncedSearch, role]);

  const { data, isLoading, isFetching, error } = useQuery<PageResponse<UserListItem>>({
    queryKey: ['admin-users', queryString],
    queryFn: () => api.get<PageResponse<UserListItem>>(`/api/v1/admin/users?${queryString}`),
    placeholderData: keepPreviousData,
  });

  if (!hasRole('ADMIN') && !hasRole('FLOW_EDITOR')) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-6 text-red-800">
        Du saknar behörighet att visa användare.
      </div>
    );
  }

  return (
    <div className="max-w-6xl">
      <div className="mb-6">
        <h1 className="page-title">Användare</h1>
        <p className="mt-1 text-sm text-gray-600">
          Översikt över alla konton i systemet. CRUD-funktioner tillkommer.
        </p>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
        <div className="p-4 border-b border-gray-200 flex flex-wrap gap-3 items-center">
          <div className="flex-1 min-w-[240px]">
            <label htmlFor="search" className="sr-only">
              Sök användare
            </label>
            <input
              id="search"
              type="search"
              placeholder="Sök på namn eller e-post…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 px-3 py-2 border"
            />
          </div>
          <div>
            <label htmlFor="role-filter" className="sr-only">
              Filtrera på roll
            </label>
            <select
              id="role-filter"
              value={role}
              onChange={(e) => setRole(e.target.value)}
              className="rounded-md border-gray-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 px-3 py-2 border"
            >
              <option value="">Alla roller</option>
              {ROLE_FILTERS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>
          {isFetching && (
            <span className="text-sm text-gray-500" aria-live="polite">
              Uppdaterar…
            </span>
          )}
        </div>

        {error ? (
          <div className="p-6 text-red-700 bg-red-50">
            Kunde inte hämta användare. Kontrollera att du har ADMIN-behörighet.
          </div>
        ) : isLoading ? (
          <div className="p-10 text-center text-gray-500">Laddar…</div>
        ) : !data || data.content.length === 0 ? (
          <div className="p-10 text-center text-gray-500">Inga användare matchar filtren.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Namn
                  </th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    E-post
                  </th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Roller
                  </th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Senast inloggad
                  </th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {data.content.map((u) => (
                  <tr key={u.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">{u.displayName || '—'}</div>
                      {u.phone && (
                        <div className="text-xs text-gray-500">{u.phone}</div>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-700">{u.email}</td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex flex-wrap gap-1">
                        {u.roles.length === 0 ? (
                          <span className="text-xs text-gray-500">Ingen roll</span>
                        ) : (
                          u.roles.map((r) => (
                            <span
                              key={r}
                              className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-800"
                            >
                              {r}
                            </span>
                          ))
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-500">
                      {formatDate(u.lastLoginAt)}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                          u.active ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                        }`}
                      >
                        {u.active ? 'Aktiv' : 'Inaktiv'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && data.totalPages > 1 && (
          <div className="px-4 py-3 border-t border-gray-200 flex items-center justify-between">
            <span className="text-sm text-gray-700">
              Visar {data.content.length} av {data.totalElements} användare
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={data.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1 text-sm border border-gray-300 rounded-md disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                Föregående
              </button>
              <span className="px-3 py-1 text-sm text-gray-600" aria-live="polite">
                Sida {data.number + 1} av {data.totalPages}
              </span>
              <button
                type="button"
                disabled={data.last}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1 text-sm border border-gray-300 rounded-md disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                Nästa
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
