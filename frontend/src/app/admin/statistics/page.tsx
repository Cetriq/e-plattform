'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api/client';

interface OverviewStats {
  totalCases: number;
  pendingCases: number;
  completedCases: number;
  totalFlows: number;
  publishedFlows: number;
  totalUsers: number;
  casesThisMonth: number;
  avgProcessingDays: number;
}

interface StatusCount {
  status: string;
  count: number;
}

interface FlowCount {
  flowId: string;
  flowName: string;
  count: number;
}

export default function StatisticsPage() {
  const [overview, setOverview] = useState<OverviewStats | null>(null);
  const [statusCounts, setStatusCounts] = useState<StatusCount[]>([]);
  const [flowCounts, setFlowCounts] = useState<FlowCount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        setLoading(true);

        const [overviewData, statusData, flowData] = await Promise.all([
          api.get<OverviewStats>('/api/v1/admin/statistics/overview').catch(() => null),
          api.get<StatusCount[]>('/api/v1/admin/statistics/cases/by-status').catch(() => []),
          api.get<FlowCount[]>('/api/v1/admin/statistics/cases/by-flow').catch(() => []),
        ]);

        if (overviewData) setOverview(overviewData);
        setStatusCounts(statusData || []);
        setFlowCounts(flowData || []);

        setError(null);
      } catch (err) {
        setError('Kunde inte hämta statistik');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    fetchStats();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4">
        <p className="text-red-800">{error}</p>
      </div>
    );
  }

  const statusLabels: Record<string, string> = {
    DRAFT: 'Utkast',
    SUBMITTED: 'Inskickad',
    IN_PROGRESS: 'Under behandling',
    WAITING_FOR_INFO: 'Väntar på komplettering',
    COMPLETED: 'Avslutad',
    CANCELLED: 'Avbruten',
  };

  const statusColors: Record<string, string> = {
    DRAFT: 'bg-gray-100 text-gray-800',
    SUBMITTED: 'bg-blue-100 text-blue-800',
    IN_PROGRESS: 'bg-yellow-100 text-yellow-800',
    WAITING_FOR_INFO: 'bg-orange-100 text-orange-800',
    COMPLETED: 'bg-green-100 text-green-800',
    CANCELLED: 'bg-red-100 text-red-800',
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">Statistik</h1>
        <p className="text-gray-600">Översikt av system- och ärendestatistik</p>
      </div>

      {/* Overview Cards */}
      {overview && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            title="Totalt antal ärenden"
            value={overview.totalCases}
            icon="📋"
          />
          <StatCard
            title="Aktiva ärenden"
            value={overview.pendingCases}
            icon="⏳"
            color="yellow"
          />
          <StatCard
            title="Avslutade ärenden"
            value={overview.completedCases}
            icon="✅"
            color="green"
          />
          <StatCard
            title="Ärenden denna månad"
            value={overview.casesThisMonth}
            icon="📈"
            color="blue"
          />
        </div>
      )}

      {/* Second row of cards */}
      {overview && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            title="E-tjänster"
            value={overview.totalFlows}
            subtitle={`${overview.publishedFlows} publicerade`}
            icon="📝"
          />
          <StatCard
            title="Användare"
            value={overview.totalUsers}
            icon="👥"
          />
          <StatCard
            title="Genomsnittlig handläggningstid"
            value={overview.avgProcessingDays.toFixed(1)}
            subtitle="dagar"
            icon="⏱️"
          />
          <StatCard
            title="System"
            value="OK"
            subtitle="Alla tjänster fungerar"
            icon="🟢"
            color="green"
          />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Cases by Status */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-medium text-gray-900 mb-4">Ärenden per status</h2>
          {statusCounts.length === 0 ? (
            <p className="text-gray-500 text-sm">Inga ärenden än</p>
          ) : (
            <div className="space-y-3">
              {statusCounts.map((item) => (
                <div key={item.status} className="flex items-center justify-between">
                  <span className={`px-2 py-1 rounded text-sm ${statusColors[item.status] || 'bg-gray-100'}`}>
                    {statusLabels[item.status] || item.status}
                  </span>
                  <span className="font-medium">{item.count}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Cases by Flow */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-medium text-gray-900 mb-4">Ärenden per e-tjänst</h2>
          {flowCounts.length === 0 ? (
            <p className="text-gray-500 text-sm">Inga ärenden än</p>
          ) : (
            <div className="space-y-3">
              {flowCounts.map((item) => (
                <div key={item.flowId} className="flex items-center justify-between">
                  <span className="text-sm text-gray-700 truncate max-w-[200px]">{item.flowName}</span>
                  <span className="font-medium">{item.count}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <p className="text-sm text-gray-500">
        Spårbarhetsloggen (vem som läst och ändrat uppgifter) finns hos rollen Informationssäkerhet &amp; dataskydd.
      </p>
    </div>
  );
}

function StatCard({
  title,
  value,
  subtitle,
  icon,
  color = 'gray',
}: {
  title: string;
  value: number | string;
  subtitle?: string;
  icon: string;
  color?: 'gray' | 'blue' | 'green' | 'yellow' | 'red';
}) {
  const bgColors = {
    gray: 'bg-gray-50',
    blue: 'bg-blue-50',
    green: 'bg-green-50',
    yellow: 'bg-yellow-50',
    red: 'bg-red-50',
  };

  return (
    <div className={`${bgColors[color]} rounded-lg p-4`}>
      <div className="flex items-center gap-3">
        <span className="text-2xl">{icon}</span>
        <div>
          <p className="text-sm text-gray-600">{title}</p>
          <p className="text-2xl font-semibold text-gray-900">{value}</p>
          {subtitle && <p className="text-xs text-gray-500">{subtitle}</p>}
        </div>
      </div>
    </div>
  );
}

