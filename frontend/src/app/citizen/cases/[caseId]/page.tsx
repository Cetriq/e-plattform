'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Download } from 'lucide-react';
import { Header } from '@/components/layout';
import { useAuth } from '@/context/AuthContext';
import {
  getCase,
  getCaseEvents,
  downloadOwnCasePdf,
  getCaseMessages,
  markCaseMessagesRead,
  sendCaseMessage,
  type CaseDetail,
} from '@/lib/api/cases';
import { api } from '@/lib/api/client';
import type { Flow } from '@/components/form';
import { getDisplayValues } from '@/lib/caseValues';
import { CaseTimeline } from '@/components/cases/CaseTimeline';
import { MessageThread } from '@/components/cases/MessageThread';
import { toast } from '@/hooks/useToast';

function StatusBadge({ caseDetail }: { caseDetail: CaseDetail }) {
  const label = caseDetail.statusName
    ?? (caseDetail.isDraft ? 'Utkast' : caseDetail.isCompleted ? 'Avslutat' : 'Under behandling');
  const color = caseDetail.statusColor ?? '#1D4ED8';
  return (
    <span
      className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium"
      style={{ backgroundColor: `${color}20`, color }}
    >
      {label}
    </span>
  );
}

export default function CaseDetailPage() {
  const params = useParams();
  const caseId = params.caseId as string;
  const { isAuthenticated, isLoading: authLoading } = useAuth();
  const queryClient = useQueryClient();
  const [isDownloading, setIsDownloading] = useState(false);

  const caseQuery = useQuery({
    queryKey: ['citizen-case', caseId],
    queryFn: () => getCase(caseId),
    enabled: isAuthenticated,
    retry: false,
  });

  // The flow gives field order, option labels and field types for displaying answers
  const flowQuery = useQuery({
    queryKey: ['flow', caseQuery.data?.flowId],
    queryFn: () => api.get<Flow>(`/api/v1/flows/${caseQuery.data?.flowId}`),
    enabled: !!caseQuery.data?.flowId,
    retry: false,
  });

  const eventsQuery = useQuery({
    queryKey: ['citizen-case-events', caseId],
    queryFn: () => getCaseEvents(caseId),
    enabled: isAuthenticated,
    retry: false,
  });

  const isSubmitted = !!caseQuery.data && !caseQuery.data.isDraft;

  const messagesQuery = useQuery({
    queryKey: ['citizen-case-messages', caseId],
    queryFn: () => getCaseMessages(caseId),
    enabled: isAuthenticated && isSubmitted,
    retry: false,
  });

  // Opening the case counts as reading the handläggare's messages
  const hasUnread = messagesQuery.data?.some((m) => m.fromManager && !m.readAt) ?? false;
  useEffect(() => {
    if (!hasUnread) return;
    markCaseMessagesRead(caseId)
      .then(() => queryClient.invalidateQueries({ queryKey: ['my-cases'] }))
      .catch(() => undefined);
  }, [hasUnread, caseId, queryClient]);

  const handleSendMessage = async (text: string) => {
    await sendCaseMessage(caseId, text);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['citizen-case-messages', caseId] }),
      queryClient.invalidateQueries({ queryKey: ['citizen-case-events', caseId] }),
    ]);
  };

  const handleDownload = async () => {
    if (!caseQuery.data) return;
    setIsDownloading(true);
    try {
      await downloadOwnCasePdf(caseId, caseQuery.data.referenceNumber);
      toast.success('PDF nedladdad');
    } catch (err) {
      toast.error('Kunde inte ladda ned PDF', 'Försök igen eller kontakta support.');
    } finally {
      setIsDownloading(false);
    }
  };

  if (!authLoading && !isAuthenticated) {
    return (
      <>
        <Header />
        <div className="container mx-auto px-4 py-8">
          <div className="bg-brand-50 border border-brand-200 rounded-lg p-8 text-center">
            <p className="text-brand-900 mb-4">Du behöver vara inloggad för att se ditt ärende.</p>
            <Link
              href="/auth/login"
              className="inline-flex items-center justify-center bg-brand-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-brand-700 transition-colors"
            >
              Logga in
            </Link>
          </div>
        </div>
      </>
    );
  }

  const loading = authLoading || caseQuery.isLoading;
  const error = caseQuery.error || eventsQuery.error;

  return (
    <>
      <Header />
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <Link
          href="/citizen/cases"
          className="inline-flex items-center text-brand-600 hover:text-brand-800 text-sm font-medium mb-6"
        >
          <ArrowLeft className="w-4 h-4 mr-1" />
          Tillbaka till mina ärenden
        </Link>

        {loading && (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-600"></div>
            <span className="ml-3 text-gray-600">Laddar ärende…</span>
          </div>
        )}

        {error && !loading && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4" role="alert">
            <p className="text-red-800">
              Kunde inte ladda ärendet. Det kanske tillhör någon annan eller har tagits bort.
            </p>
          </div>
        )}

        {caseQuery.data && (
          <article className="space-y-8">
            <header className="bg-white rounded-lg border p-6">
              <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
                <div>
                  <h1 className="page-title mb-1">
                    {caseQuery.data.flowName}
                  </h1>
                  <p className="text-sm text-gray-500 mb-3">
                    Referens: <span className="font-mono">{caseQuery.data.referenceNumber}</span>
                  </p>
                  <StatusBadge caseDetail={caseQuery.data} />
                </div>
                <button
                  type="button"
                  onClick={handleDownload}
                  disabled={isDownloading}
                  className="inline-flex items-center gap-2 bg-brand-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-brand-700 disabled:bg-brand-300 transition-colors"
                >
                  <Download className="w-4 h-4" />
                  {isDownloading ? 'Förbereder…' : 'Ladda ner som PDF'}
                </button>
              </div>
            </header>

            {isSubmitted && (
              <section className="bg-white rounded-lg border p-6" aria-labelledby="messages-heading">
                <h2 id="messages-heading" className="text-lg font-semibold text-gray-900 mb-1">
                  Meddelanden
                </h2>
                <p className="text-sm text-gray-500 mb-4">
                  Här kan du och handläggaren skriva till varandra om ärendet.
                </p>
                <MessageThread
                  messages={(messagesQuery.data ?? []).map((m) => ({
                    id: m.id,
                    message: m.message,
                    createdAt: m.createdAt,
                    authorName: m.fromManager ? m.authorName : 'Du',
                    mine: !m.fromManager,
                  }))}
                  onSend={handleSendMessage}
                  placeholder="Skriv till handläggaren…"
                  emptyText="Inga meddelanden ännu."
                />
              </section>
            )}

            <section className="bg-white rounded-lg border p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Händelser</h2>
              {eventsQuery.isLoading ? (
                <p className="text-gray-500">Laddar händelser…</p>
              ) : eventsQuery.data ? (
                <CaseTimeline events={eventsQuery.data} />
              ) : (
                <p className="text-gray-500">Inga händelser att visa.</p>
              )}
            </section>

            <section className="bg-white rounded-lg border p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Inlämnade uppgifter</h2>
              {caseQuery.data.values && caseQuery.data.values.length > 0 ? (
                <dl className="divide-y divide-gray-200">
                  {getDisplayValues(caseQuery.data.values, flowQuery.data).map((answer) => (
                    <div key={answer.id} className="py-3 grid grid-cols-1 md:grid-cols-3 gap-2">
                      <dt className="text-sm font-medium text-gray-500">{answer.label}</dt>
                      <dd className="md:col-span-2 text-sm text-gray-900 whitespace-pre-wrap">
                        {answer.value}
                      </dd>
                    </div>
                  ))}
                </dl>
              ) : (
                <p className="text-gray-500 italic">Inga uppgifter lämnade ännu.</p>
              )}
            </section>
          </article>
        )}
      </div>
    </>
  );
}
