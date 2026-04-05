'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getAllFlows,
  deleteFlow,
  publishFlow,
  archiveFlow,
  duplicateFlow,
  type FlowSummary,
} from '@/lib/api/admin';

const statusLabels: Record<string, { label: string; bg: string; text: string }> = {
  DRAFT: { label: 'Utkast', bg: 'bg-gray-100', text: 'text-gray-700' },
  PUBLISHED: { label: 'Publicerad', bg: 'bg-green-100', text: 'text-green-700' },
  ARCHIVED: { label: 'Arkiverad', bg: 'bg-yellow-100', text: 'text-yellow-700' },
};

export default function FlowsListPage() {
  const queryClient = useQueryClient();
  const [currentPage, setCurrentPage] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newFlowName, setNewFlowName] = useState('');
  const [actionFlow, setActionFlow] = useState<FlowSummary | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const { data: flowsResponse, isLoading, error } = useQuery({
    queryKey: ['admin-flows', currentPage],
    queryFn: () => getAllFlows(currentPage),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteFlow,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flows'] });
      setShowDeleteConfirm(false);
      setActionFlow(null);
    },
  });

  const publishMutation = useMutation({
    mutationFn: publishFlow,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flows'] });
    },
  });

  const archiveMutation = useMutation({
    mutationFn: archiveFlow,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flows'] });
    },
  });

  const duplicateMutation = useMutation({
    mutationFn: duplicateFlow,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flows'] });
    },
  });

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('sv-SE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  const handleDelete = () => {
    if (actionFlow) {
      deleteMutation.mutate(actionFlow.id);
    }
  };

  return (
    <div>
      {/* Page header */}
      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">E-tjänster</h1>
          <p className="text-gray-600 mt-1">
            Skapa och hantera e-tjänster och formulär.
          </p>
        </div>
        <Link
          href="/admin/flows/new"
          className="flex items-center gap-2 bg-purple-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-purple-700 transition-colors"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Skapa ny e-tjänst
        </Link>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg shadow-sm border p-4 mb-6">
        <div className="flex gap-4 items-center">
          <div className="flex-1">
            <input
              type="text"
              placeholder="Sök e-tjänster..."
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
          </div>
          <select className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500">
            <option value="">Alla statusar</option>
            <option value="DRAFT">Utkast</option>
            <option value="PUBLISHED">Publicerad</option>
            <option value="ARCHIVED">Arkiverad</option>
          </select>
          <select className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500">
            <option value="">Alla kategorier</option>
          </select>
        </div>
      </div>

      {/* Flows table */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600"></div>
          <span className="ml-3 text-gray-600">Laddar e-tjänster...</span>
        </div>
      ) : error ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <p className="text-red-800">
            Kunde inte ladda e-tjänster. Försök igen senare.
          </p>
        </div>
      ) : flowsResponse?.content?.length === 0 ? (
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
            Inga e-tjänster ännu
          </h3>
          <p className="text-gray-600 mb-4">
            Kom igång genom att skapa din första e-tjänst.
          </p>
          <Link
            href="/admin/flows/new"
            className="inline-flex items-center gap-2 bg-purple-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-purple-700"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Skapa e-tjänst
          </Link>
        </div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow-sm border overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                    E-tjänst
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Kategori
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Senast ändrad
                  </th>
                  <th className="px-6 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {flowsResponse?.content?.map((flow: FlowSummary) => {
                  const status = statusLabels[flow.status] || statusLabels.DRAFT;

                  return (
                    <tr key={flow.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4">
                        <div>
                          <Link
                            href={`/admin/flows/${flow.id}`}
                            className="font-medium text-gray-900 hover:text-purple-600"
                          >
                            {flow.name}
                          </Link>
                          {flow.shortDescription && (
                            <p className="text-sm text-gray-500 truncate max-w-md">
                              {flow.shortDescription}
                            </p>
                          )}
                          <p className="text-xs text-gray-400 mt-1">
                            Version {flow.version}
                          </p>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${status.bg} ${status.text}`}
                        >
                          {status.label}
                        </span>
                        {!flow.enabled && flow.status === 'PUBLISHED' && (
                          <span className="ml-2 text-xs text-orange-600">(Inaktiverad)</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-600">
                        {flow.categoryName || '-'}
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-600">
                        {formatDate(flow.updatedAt)}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center justify-end gap-2">
                          <Link
                            href={`/admin/flows/${flow.id}`}
                            className="text-purple-600 hover:text-purple-800 text-sm font-medium"
                          >
                            Redigera
                          </Link>
                          <div className="relative group">
                            <button className="p-1 text-gray-400 hover:text-gray-600 rounded">
                              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                              </svg>
                            </button>
                            <div className="hidden group-hover:block absolute right-0 top-full mt-1 bg-white border rounded-lg shadow-lg py-1 min-w-[160px] z-10">
                              {flow.status === 'DRAFT' && (
                                <button
                                  onClick={() => publishMutation.mutate(flow.id)}
                                  className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                                >
                                  Publicera
                                </button>
                              )}
                              {flow.status === 'PUBLISHED' && (
                                <button
                                  onClick={() => archiveMutation.mutate(flow.id)}
                                  className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                                >
                                  Arkivera
                                </button>
                              )}
                              <button
                                onClick={() => duplicateMutation.mutate(flow.id)}
                                className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                              >
                                Duplicera
                              </button>
                              <Link
                                href={`/citizen/services/${flow.id}`}
                                className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                              >
                                Förhandsgranska
                              </Link>
                              {flow.status === 'DRAFT' && (
                                <button
                                  onClick={() => {
                                    setActionFlow(flow);
                                    setShowDeleteConfirm(true);
                                  }}
                                  className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50"
                                >
                                  Ta bort
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {flowsResponse && flowsResponse.totalPages > 1 && (
            <div className="flex items-center justify-between mt-4">
              <p className="text-sm text-gray-600">
                Visar {flowsResponse.number * flowsResponse.size + 1}-
                {Math.min(
                  (flowsResponse.number + 1) * flowsResponse.size,
                  flowsResponse.totalElements
                )}{' '}
                av {flowsResponse.totalElements} e-tjänster
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                  disabled={flowsResponse.first}
                  className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
                >
                  Föregående
                </button>
                <button
                  onClick={() => setCurrentPage((p) => p + 1)}
                  disabled={flowsResponse.last}
                  className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
                >
                  Nästa
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {/* Delete confirmation modal */}
      {showDeleteConfirm && actionFlow && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6">
            <h3 className="text-lg font-bold text-gray-900 mb-2">Ta bort e-tjänst</h3>
            <p className="text-gray-600 mb-4">
              Är du säker på att du vill ta bort &quot;{actionFlow.name}&quot;? Denna åtgärd går inte att
              ångra.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => {
                  setShowDeleteConfirm(false);
                  setActionFlow(null);
                }}
                className="px-4 py-2 border rounded-lg text-gray-700 hover:bg-gray-50"
              >
                Avbryt
              </button>
              <button
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
              >
                {deleteMutation.isPending ? 'Tar bort...' : 'Ta bort'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
