'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import React, { useEffect, useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/context/AuthContext';
import {
  getManagerCaseDetail,
  changeCaseStatus,
  downloadCasePdf,
  addExternalMessage,
  addInternalMessage,
  assignCase,
  getAssignees,
  type ChangeStatusRequest,
  type ManagerCaseDetail,
} from '@/lib/api/manager';
import { getCaseEvents, markCaseMessagesRead } from '@/lib/api/cases';
import { MessageThread } from '@/components/cases/MessageThread';
import { CaseTimeline } from '@/components/cases/CaseTimeline';
import { toast } from '@/hooks/useToast';
import { api } from '@/lib/api/client';
import type { Flow } from '@/components/form';
import { getDisplayValues } from '@/lib/caseValues';

const statusTypeLabels: Record<string, string> = {
  DRAFT: 'Utkast',
  SUBMITTED: 'Inskickad',
  IN_PROGRESS: 'Under behandling',
  WAITING_FOR_USER: 'Väntar på komplettering',
  WAITING_FOR_EXTERNAL: 'Väntar på extern',
  COMPLETED: 'Avslutad',
  CANCELLED: 'Avbruten',
  ARCHIVED: 'Arkiverad',
};

const priorityLabels: Record<string, { label: string; color: string }> = {
  LOW: { label: 'Låg', color: 'bg-gray-100 text-gray-700' },
  NORMAL: { label: 'Normal', color: 'bg-blue-100 text-blue-700' },
  HIGH: { label: 'Hög', color: 'bg-orange-100 text-orange-700' },
  URGENT: { label: 'Brådskande', color: 'bg-red-100 text-red-700' },
};


export default function ManagerCaseDetailPage() {
  const params = useParams();
  const caseId = params.caseId as string;
  const { isAuthenticated, user } = useAuth();
  const queryClient = useQueryClient();

  const [showStatusModal, setShowStatusModal] = useState(false);
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [statusComment, setStatusComment] = useState('');
  const [isDownloadingPdf, setIsDownloadingPdf] = useState(false);
  const [messageTab, setMessageTab] = useState<'citizen' | 'internal'>('citizen');
  const messagesRef = useRef<HTMLDivElement>(null);

  const { data: caseData, isLoading, error } = useQuery({
    queryKey: ['manager-case', caseId],
    queryFn: () => getManagerCaseDetail(caseId),
    enabled: isAuthenticated && !!caseId,
  });

  // The flow gives field order, option labels and field types for displaying answers
  const { data: flow } = useQuery({
    queryKey: ['flow', caseData?.flowId],
    queryFn: () => api.get<Flow>(`/api/v1/flows/${caseData?.flowId}`),
    enabled: !!caseData?.flowId,
    retry: false,
  });

  const { data: events } = useQuery({
    queryKey: ['manager-case-events', caseId],
    queryFn: () => getCaseEvents(caseId),
    enabled: isAuthenticated && !!caseId,
  });

  const { data: assignees } = useQuery({
    queryKey: ['assignees'],
    queryFn: getAssignees,
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
  });

  const refreshCase = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['manager-case', caseId] }),
      queryClient.invalidateQueries({ queryKey: ['manager-case-events', caseId] }),
      queryClient.invalidateQueries({ queryKey: ['manager-cases'] }),
    ]);

  // Opening the case counts as reading the citizen's messages
  const hasUnread = caseData?.externalMessages?.some((m) => !m.fromManager && !m.readAt) ?? false;
  useEffect(() => {
    if (!hasUnread) return;
    markCaseMessagesRead(caseId)
      .then(() => queryClient.invalidateQueries({ queryKey: ['manager-cases'] }))
      .catch(() => undefined);
  }, [hasUnread, caseId, queryClient]);

  const statusMutation = useMutation({
    mutationFn: (request: ChangeStatusRequest) =>
      changeCaseStatus(caseId, request),
    onSuccess: () => {
      refreshCase();
      setShowStatusModal(false);
      setSelectedStatus('');
      setStatusComment('');
      toast.success('Status ändrad');
    },
  });

  const assignMutation = useMutation({
    mutationFn: (userId: string | null) => assignCase(caseId, userId),
    onSuccess: (_, userId) => {
      refreshCase();
      toast.success(userId ? 'Ärendet tilldelat' : 'Tilldelningen borttagen');
    },
    onError: () => toast.error('Kunde inte ändra tilldelningen'),
  });

  const allowedStatuses = (caseData?.allowedTransitions ?? [])
    .map((t) => ({
      ...t,
      status: caseData?.statusDefinitions.find((s) => s.id === t.statusId),
    }))
    .filter((t) => t.status)
    .sort((a, b) => (a.status!.sortOrder ?? 0) - (b.status!.sortOrder ?? 0));
  const selectedTransition = allowedStatuses.find((t) => t.statusId === selectedStatus);
  const commentRequired = !!selectedTransition?.requiresComment;

  const sendToCitizen = async (text: string) => {
    await addExternalMessage(caseId, text);
    await refreshCase();
  };

  const addNote = async (text: string) => {
    await addInternalMessage(caseId, text);
    await refreshCase();
  };

  const openMessages = () => {
    setMessageTab('citizen');
    messagesRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    requestAnimationFrame(() => messagesRef.current?.querySelector('textarea')?.focus());
  };

  const handleStatusChange = () => {
    if (!selectedStatus || (commentRequired && !statusComment.trim())) return;
    statusMutation.mutate({
      statusId: selectedStatus,
      comment: statusComment || undefined,
    });
  };

  const handleDownloadPdf = async () => {
    setIsDownloadingPdf(true);
    try {
      await downloadCasePdf(caseId, caseData?.referenceNumber);
    } catch (error) {
      console.error('Failed to download PDF:', error);
    } finally {
      setIsDownloadingPdf(false);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('sv-SE', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="bg-white p-8 rounded-lg shadow-md text-center max-w-md">
          <h2 className="text-xl font-bold mb-2">Inloggning krävs</h2>
          <Link
            href={`/auth/login?redirect=/manager/cases/${caseId}`}
            className="text-brand-600 hover:underline"
          >
            Logga in
          </Link>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-100">
        <ManagerHeader userName={user?.displayName} />
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-600"></div>
            <span className="ml-3 text-gray-600">Laddar ärende...</span>
          </div>
        </main>
      </div>
    );
  }

  if (error || !caseData) {
    return (
      <div className="min-h-screen bg-gray-100">
        <ManagerHeader userName={user?.displayName} />
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="bg-red-50 border border-red-200 rounded-lg p-8 text-center">
            <h3 className="text-lg font-medium text-red-900 mb-2">
              Kunde inte ladda ärendet
            </h3>
            <Link
              href="/manager/dashboard"
              className="text-red-600 hover:underline"
            >
              Tillbaka till ärendelistan
            </Link>
          </div>
        </main>
      </div>
    );
  }

  const priority = priorityLabels[(caseData as ManagerCaseDetail & { priority?: string }).priority || 'NORMAL'];

  return (
    <div className="min-h-screen bg-gray-100">
      <ManagerHeader userName={user?.displayName} />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-sm text-gray-500 mb-6">
          <Link href="/manager/dashboard" className="hover:text-brand-600">
            Ärenden
          </Link>
          <span>/</span>
          <span className="text-gray-900">{caseData.referenceNumber}</span>
        </nav>

        {/* Header */}
        <div className="card p-6 mb-6">
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-3 mb-2">
                <h1 className="page-title">
                  {caseData.flowName}
                </h1>
                <span className={`px-2 py-1 text-xs font-medium rounded-full ${priority.color}`}>
                  {priority.label}
                </span>
              </div>
              <p className="text-gray-500">{caseData.referenceNumber}</p>
            </div>
            <div className="flex items-center gap-3">
              {caseData.statusName && (
                <span
                  className="px-3 py-1 text-sm font-medium rounded-full"
                  style={
                    caseData.statusColor
                      ? {
                          backgroundColor: `${caseData.statusColor}20`,
                          color: caseData.statusColor,
                        }
                      : { backgroundColor: '#dbeafe', color: '#1d4ed8' }
                  }
                >
                  {caseData.statusName}
                </span>
              )}
              <button
                onClick={() => setShowStatusModal(true)}
                className="px-4 py-2 bg-brand-600 text-white rounded-lg hover:bg-brand-700 text-sm font-medium"
              >
                Ändra status
              </button>
            </div>
          </div>

          {/* Meta info */}
          <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-4 pt-4 border-t">
            <div>
              <dt className="text-xs text-gray-500 uppercase tracking-wide">Inkom</dt>
              <dd className="text-sm font-medium text-gray-900 mt-1">
                {caseData.submittedAt ? formatDate(caseData.submittedAt) : '-'}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-gray-500 uppercase tracking-wide">Skapad</dt>
              <dd className="text-sm font-medium text-gray-900 mt-1">
                {formatDate(caseData.createdAt)}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-gray-500 uppercase tracking-wide">Uppdaterad</dt>
              <dd className="text-sm font-medium text-gray-900 mt-1">
                {formatDate(caseData.updatedAt)}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-gray-500 uppercase tracking-wide">Steg</dt>
              <dd className="text-sm font-medium text-gray-900 mt-1">
                {caseData.currentStepIndex + 1} av {caseData.totalSteps}
              </dd>
            </div>
          </div>
        </div>

        {/* Content grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Main content - Form values */}
          <div className="lg:col-span-2 space-y-6">
            <div className="card">
              <div className="px-6 py-4 border-b">
                <h2 className="text-lg font-semibold text-gray-900">
                  Formulärdata
                </h2>
              </div>
              <div className="p-6">
                {caseData.values && caseData.values.length > 0 ? (
                  <dl className="space-y-4">
                    {getDisplayValues(caseData.values, flow).map((answer) => (
                      <div key={answer.id} className="border-b pb-4 last:border-b-0 last:pb-0">
                        <dt className="text-sm font-medium text-gray-500">{answer.label}</dt>
                        <dd className="mt-1 text-gray-900 whitespace-pre-wrap">{answer.value}</dd>
                      </div>
                    ))}
                  </dl>
                ) : (
                  <p className="text-gray-500 text-center py-4">
                    Ingen formulärdata tillgänglig.
                  </p>
                )}
              </div>
            </div>

            {/* Communication */}
            <div ref={messagesRef} className="card scroll-mt-4">
              <div className="px-6 pt-4 border-b">
                <h2 className="text-lg font-semibold text-gray-900">Kommunikation</h2>
                <div className="mt-3 flex gap-6" role="tablist" aria-label="Kommunikation">
                  {([
                    ['citizen', `Med sökande (${caseData.externalMessages?.length ?? 0})`],
                    ['internal', `Interna anteckningar (${caseData.internalMessages?.length ?? 0})`],
                  ] as const).map(([tab, label]) => (
                    <button
                      key={tab}
                      type="button"
                      role="tab"
                      aria-selected={messageTab === tab}
                      onClick={() => setMessageTab(tab)}
                      className={`pb-3 text-sm font-medium border-b-2 -mb-px ${
                        messageTab === tab
                          ? 'border-brand-600 text-brand-600'
                          : 'border-transparent text-gray-500 hover:text-gray-700'
                      }`}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="p-6" role="tabpanel">
                {messageTab === 'citizen' ? (
                  <MessageThread
                    messages={[...(caseData.externalMessages ?? [])]
                      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
                      .map((m) => ({
                        id: m.id,
                        message: m.message,
                        createdAt: m.createdAt,
                        authorName: m.userName,
                        mine: m.fromManager,
                      }))}
                    onSend={sendToCitizen}
                    placeholder="Skriv till den sökande…"
                    emptyText="Inga meddelanden med den sökande ännu."
                  />
                ) : (
                  <MessageThread
                    variant="notes"
                    messages={[...(caseData.internalMessages ?? [])]
                      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
                      .map((m) => ({
                        id: m.id,
                        message: m.message,
                        createdAt: m.createdAt,
                        authorName: m.userName,
                        mine: m.userId === user?.id,
                      }))}
                    onSend={addNote}
                    placeholder="Anteckning som bara handläggare ser…"
                    sendLabel="Spara anteckning"
                    emptyText="Inga interna anteckningar."
                  />
                )}
              </div>
            </div>

            {/* Timeline */}
            <div className="card">
              <div className="px-6 py-4 border-b">
                <h2 className="text-lg font-semibold text-gray-900">Händelser</h2>
              </div>
              <div className="p-6">
                {events && events.length > 0 ? (
                  <CaseTimeline events={events} />
                ) : (
                  <p className="text-gray-500 text-sm">Inga händelser.</p>
                )}
              </div>
            </div>
          </div>

          {/* Sidebar */}
          <div className="space-y-6">
            {/* Actions */}
            <div className="card">
              <div className="px-6 py-4 border-b">
                <h3 className="text-lg font-semibold text-gray-900">Åtgärder</h3>
              </div>
              <div className="p-4 space-y-2">
                <button
                  onClick={() => setShowStatusModal(true)}
                  className="w-full px-4 py-2 text-left text-sm rounded hover:bg-gray-50 flex items-center gap-2"
                >
                  <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  Ändra status
                </button>
                <button
                  type="button"
                  onClick={openMessages}
                  className="w-full px-4 py-2 text-left text-sm rounded hover:bg-gray-50 flex items-center gap-2"
                >
                  <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                  </svg>
                  Skicka meddelande
                </button>
                <button
                  onClick={handleDownloadPdf}
                  disabled={isDownloadingPdf}
                  className="w-full px-4 py-2 text-left text-sm rounded hover:bg-gray-50 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isDownloadingPdf ? (
                    <svg className="w-5 h-5 text-gray-400 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                  ) : (
                    <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                  )}
                  {isDownloadingPdf ? 'Laddar ner...' : 'Ladda ner PDF'}
                </button>
              </div>
            </div>

            {/* Assignment */}
            <div className="card">
              <div className="px-6 py-4 border-b">
                <h3 className="text-lg font-semibold text-gray-900">Ansvarig handläggare</h3>
              </div>
              <div className="p-4 space-y-3 text-sm">
                <p className="text-gray-900">
                  {caseData.assignedToName ?? <span className="text-gray-500">Ej tilldelad</span>}
                </p>
                {caseData.assignedToId !== user?.id && (
                  <button
                    type="button"
                    onClick={() => user && assignMutation.mutate(user.id)}
                    disabled={assignMutation.isPending}
                    className="w-full px-4 py-2 bg-brand-600 text-white rounded-lg hover:bg-brand-700 disabled:opacity-50"
                  >
                    Tilldela mig
                  </button>
                )}
                <div>
                  <label htmlFor="assignee" className="block text-gray-500 mb-1">
                    Ändra ansvarig
                  </label>
                  <select
                    id="assignee"
                    value={caseData.assignedToId ?? ''}
                    onChange={(e) => assignMutation.mutate(e.target.value || null)}
                    disabled={assignMutation.isPending}
                    className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-500"
                  >
                    <option value="">Ingen</option>
                    {assignees?.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            {/* Quick info */}
            <div className="card">
              <div className="px-6 py-4 border-b">
                <h3 className="text-lg font-semibold text-gray-900">Information</h3>
              </div>
              <div className="p-4 space-y-3 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">E-tjänst</span>
                  <span className="text-gray-900">{caseData.flowName}</span>
                </div>
                {caseData.typeName && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Typ</span>
                    <span className="text-gray-900">{caseData.typeName}</span>
                  </div>
                )}
                {caseData.categoryName && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Kategori</span>
                    <span className="text-gray-900">{caseData.categoryName}</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Status change modal */}
      {showStatusModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div
            className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="status-dialog-title"
          >
            <div className="px-6 py-4 border-b">
              <h3 id="status-dialog-title" className="text-lg font-semibold text-gray-900">Ändra status</h3>
              <p className="text-sm text-gray-500 mt-1">Nuvarande status: {caseData.statusName}</p>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label htmlFor="new-status" className="block text-sm font-medium text-gray-700 mb-2">
                  Ny status
                </label>
                {allowedStatuses.length === 0 ? (
                  <p className="text-sm text-gray-500">
                    Ärendet kan inte byta status från nuvarande status.
                  </p>
                ) : (
                  <select
                    id="new-status"
                    value={selectedStatus}
                    onChange={(e) => setSelectedStatus(e.target.value)}
                    className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-500"
                  >
                    <option value="">Välj status...</option>
                    {allowedStatuses.map((t) => (
                      <option key={t.statusId} value={t.statusId}>
                        {t.status!.name}
                      </option>
                    ))}
                  </select>
                )}
              </div>
              <div>
                <label htmlFor="status-comment" className="block text-sm font-medium text-gray-700 mb-2">
                  Kommentar {commentRequired ? '(krävs)' : '(valfritt)'}
                </label>
                <textarea
                  id="status-comment"
                  required={commentRequired}
                  value={statusComment}
                  onChange={(e) => setStatusComment(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-500"
                  placeholder="Lägg till en kommentar..."
                />
              </div>
              {statusMutation.isError && (
                <p className="text-sm text-red-600" role="alert">
                  {(statusMutation.error as { message?: string })?.message || 'Kunde inte ändra status. Försök igen.'}
                </p>
              )}
            </div>
            <div className="px-6 py-4 border-t bg-gray-50 flex justify-end gap-3 rounded-b-lg">
              <button
                onClick={() => {
                  setShowStatusModal(false);
                  setSelectedStatus('');
                  setStatusComment('');
                }}
                className="px-4 py-2 text-gray-700 hover:text-gray-900"
              >
                Avbryt
              </button>
              <button
                onClick={handleStatusChange}
                disabled={!selectedStatus || (commentRequired && !statusComment.trim()) || statusMutation.isPending}
                className="px-4 py-2 bg-brand-600 text-white rounded-lg hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {statusMutation.isPending ? 'Sparar...' : 'Spara'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ManagerHeader({ userName }: { userName?: string }) {
  return (
    <header className="bg-white border-b shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center gap-4">
            <Link href="/manager/dashboard" className="flex items-center gap-2">
              <div className="w-8 h-8 bg-brand-600 rounded flex items-center justify-center">
                <span className="text-white font-bold text-sm">E</span>
              </div>
              <span className="font-semibold text-gray-900">Handläggare</span>
            </Link>
            <nav className="hidden md:flex gap-6 ml-8">
              <Link
                href="/manager/dashboard"
                className="text-brand-600 font-medium"
              >
                Ärenden
              </Link>
              <Link
                href="/citizen/services"
                className="text-gray-600 hover:text-gray-900"
              >
                E-tjänster
              </Link>
            </nav>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-600">{userName}</span>
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
  );
}
