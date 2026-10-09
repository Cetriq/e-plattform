'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { Header } from '@/components/layout';
import { useAuth } from '@/context/AuthContext';
import { getCasesForUser, type CaseSummary } from '@/lib/api/cases';
import { MobileCard, MobileCardList } from '@/components/ui/MobileCard';

const statusColors: Record<string, { bg: string; text: string; label: string }> = {
  DRAFT: { bg: 'bg-gray-100', text: 'text-gray-700', label: 'Utkast' },
  SUBMITTED: { bg: 'bg-blue-100', text: 'text-blue-700', label: 'Inskickad' },
  IN_PROGRESS: { bg: 'bg-yellow-100', text: 'text-yellow-700', label: 'Under behandling' },
  WAITING_FOR_COMPLETION: { bg: 'bg-orange-100', text: 'text-orange-700', label: 'Komplettering begärd' },
  COMPLETED: { bg: 'bg-green-100', text: 'text-green-700', label: 'Avslutad' },
  REJECTED: { bg: 'bg-red-100', text: 'text-red-700', label: 'Avslagen' },
};

// Map status to colors for MobileCard
const statusToColors: Record<string, { color: string; bgColor: string }> = {
  DRAFT: { color: '#374151', bgColor: '#F3F4F6' },
  SUBMITTED: { color: '#1D4ED8', bgColor: '#DBEAFE' },
  IN_PROGRESS: { color: '#A16207', bgColor: '#FEF3C7' },
  WAITING_FOR_COMPLETION: { color: '#C2410C', bgColor: '#FFEDD5' },
  COMPLETED: { color: '#15803D', bgColor: '#DCFCE7' },
  REJECTED: { color: '#DC2626', bgColor: '#FEE2E2' },
};

function unreadLabel(count: number) {
  return count === 1 ? '1 nytt meddelande' : `${count} nya meddelanden`;
}

export default function CasesPage() {
  const { isAuthenticated, isLoading: authLoading, user } = useAuth();

  const { data: casesResponse, isLoading, error } = useQuery({
    queryKey: ['my-cases', user?.id],
    queryFn: async () => {
      if (!user?.id) throw new Error('User not authenticated');
      return getCasesForUser(user.id);
    },
    retry: false,
    enabled: isAuthenticated && !!user?.id,
  });

  const cases = casesResponse?.content;

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('sv-SE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  const getStatusInfo = (c: CaseSummary) => {
    const statusKey = c.isDraft ? 'DRAFT' : (c.isCompleted ? 'COMPLETED' : 'IN_PROGRESS');
    const status = statusColors[statusKey] || statusColors.DRAFT;
    const colors = statusToColors[statusKey] || statusToColors.DRAFT;
    const statusLabel = c.statusName || status.label;
    return {
      label: statusLabel,
      color: c.statusColor || colors.color,
      bgColor: c.statusColor ? `${c.statusColor}20` : colors.bgColor,
      status,
    };
  };

  return (
    <main className="min-h-screen bg-gray-50">
      <Header />

      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Mina ärenden</h1>
        <p className="text-gray-600 mb-8">
          Här ser du alla dina pågående och avslutade ärenden.
        </p>

        {!authLoading && !isAuthenticated && (
          <div className="bg-brand-50 border border-brand-200 rounded-lg p-8 text-center mb-8">
            <svg
              className="w-12 h-12 text-brand-500 mx-auto mb-4"
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
            <h3 className="text-lg font-medium text-brand-900 mb-2">
              Logga in för att se dina ärenden
            </h3>
            <p className="text-brand-700 mb-4">
              Du behöver logga in med BankID eller annan e-legitimation för att se dina ärenden.
            </p>
            <Link
              href="/auth/login"
              className="inline-flex items-center justify-center bg-brand-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-brand-700 transition-colors"
            >
              Logga in
            </Link>
          </div>
        )}

        {isAuthenticated && isLoading && (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-600"></div>
            <span className="ml-3 text-gray-600">Laddar dina ärenden...</span>
          </div>
        )}

        {isAuthenticated && error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-8" role="alert">
            <p className="text-red-800">
              Kunde inte ladda dina ärenden. Försök igen senare.
            </p>
          </div>
        )}

        {isAuthenticated && !isLoading && !error && cases?.length === 0 && (
          <div className="bg-white border rounded-lg p-8 text-center">
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
              Inga ärenden ännu
            </h3>
            <p className="text-gray-600 mb-4">
              Du har inte startat några ärenden än. Utforska våra e-tjänster för att komma igång.
            </p>
            <Link
              href="/citizen/services"
              className="inline-flex items-center justify-center bg-brand-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-brand-700 transition-colors"
            >
              Utforska tjänster
            </Link>
          </div>
        )}

        {isAuthenticated && cases && cases.length > 0 && (
          <>
            {/* Mobile view - Cards */}
            <div className="md:hidden">
              <MobileCardList>
                {cases.map((c: CaseSummary) => {
                  const statusInfo = getStatusInfo(c);
                  return (
                    <MobileCard
                      key={c.id}
                      title={c.flowName}
                      subtitle={c.referenceNumber}
                      status={{
                        label: statusInfo.label,
                        color: statusInfo.color,
                        bgColor: statusInfo.bgColor,
                      }}
                      fields={[
                        {
                          label: 'Steg',
                          value: `${c.currentStepIndex + 1} av ${c.totalSteps}`,
                        },
                        {
                          label: 'Uppdaterad',
                          value: formatDate(c.updatedAt),
                        },
                        ...(c.unreadMessages
                          ? [{ label: 'Meddelanden', value: unreadLabel(c.unreadMessages) }]
                          : []),
                      ]}
                      actions={[
                        {
                          label: c.isDraft ? 'Fortsätt' : 'Visa',
                          href: c.isDraft
                            ? `/citizen/services/${c.flowId}?caseId=${c.id}`
                            : `/citizen/cases/${c.id}`,
                          variant: 'primary',
                        },
                      ]}
                    />
                  );
                })}
              </MobileCardList>
            </div>

            {/* Desktop view - Table */}
            <div className="hidden md:block bg-white rounded-lg border overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b sticky top-0">
                    <tr>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">
                        Ärende
                      </th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">
                        Status
                      </th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">
                        Steg
                      </th>
                      <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">
                        Senast uppdaterad
                      </th>
                      <th className="px-6 py-3"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {cases.map((c: CaseSummary) => {
                      const statusInfo = getStatusInfo(c);
                      return (
                        <tr key={c.id} className="hover:bg-gray-50 transition-colors">
                          <td className="px-6 py-4">
                            <div>
                              <p className="font-medium text-gray-900">{c.flowName}</p>
                              <p className="text-sm text-gray-500">{c.referenceNumber}</p>
                              {!!c.unreadMessages && (
                                <p className="mt-1 inline-flex items-center gap-1 text-xs font-medium text-brand-700">
                                  <span className="w-2 h-2 rounded-full bg-brand-600" aria-hidden="true" />
                                  {unreadLabel(c.unreadMessages)}
                                </p>
                              )}
                            </div>
                          </td>
                          <td className="px-6 py-4">
                            <span
                              className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${statusInfo.status.bg} ${statusInfo.status.text}`}
                              style={c.statusColor ? { backgroundColor: `${c.statusColor}20`, color: c.statusColor } : {}}
                            >
                              {statusInfo.label}
                            </span>
                          </td>
                          <td className="px-6 py-4 text-sm text-gray-600">
                            Steg {c.currentStepIndex + 1} av {c.totalSteps}
                          </td>
                          <td className="px-6 py-4 text-sm text-gray-600">
                            {formatDate(c.updatedAt)}
                          </td>
                          <td className="px-6 py-4 text-right">
                            <Link
                              href={c.isDraft ? `/citizen/services/${c.flowId}?caseId=${c.id}` : `/citizen/cases/${c.id}`}
                              className="text-brand-600 hover:text-brand-800 font-medium text-sm"
                            >
                              {c.isDraft ? 'Fortsätt' : 'Visa'}
                            </Link>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
      </div>
    </main>
  );
}
