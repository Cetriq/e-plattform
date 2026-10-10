'use client';

import { useQuery } from '@tanstack/react-query';
import { getOpsStatus } from '@/lib/api/ops';

const STATUS_STYLES: Record<string, string> = {
  UP: 'bg-green-100 text-green-800',
  DOWN: 'bg-red-100 text-red-800',
  DISABLED: 'bg-gray-100 text-gray-700',
};
const STATUS_LABELS: Record<string, string> = { UP: 'I drift', DOWN: 'Nere', DISABLED: 'Avstängd' };

function formatUptime(seconds: number) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return h > 0 ? `${h} h ${m} min` : `${m} min`;
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('sv-SE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export default function OpsStatusPage() {
  const { data, isLoading, error, dataUpdatedAt, refetch, isFetching } = useQuery({
    queryKey: ['ops-status'],
    queryFn: getOpsStatus,
    refetchInterval: 30_000,
  });

  if (isLoading) return <p className="text-gray-500">Laddar…</p>;
  if (error || !data) return <p className="text-red-700" role="alert">Driftstatus kunde inte hämtas.</p>;

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
        <div>
          <h1 className="page-title">Driftstatus</h1>
          <p className="page-lead">
            Version {data.version} · uppdateras var 30:e sekund (senast {new Date(dataUpdatedAt).toLocaleTimeString('sv-SE')})
          </p>
        </div>
        <button type="button" onClick={() => refetch()} disabled={isFetching}
                className="btn-secondary">
          Uppdatera
        </button>
      </div>

      <div
        role="status"
        className={`p-4 rounded-lg border ${data.overall === 'UP' ? 'bg-green-50 border-green-200 text-green-900' : 'bg-red-50 border-red-200 text-red-900'}`}
      >
        {data.overall === 'UP' ? 'Alla komponenter är i drift.' : 'Minst en komponent är nere.'}
        {data.errorsLast24h > 0 && ` ${data.errorsLast24h} fel det senaste dygnet – se systemloggen.`}
      </div>

      <section className="grid md:grid-cols-3 gap-4" aria-label="Komponenter">
        {data.components.map((c) => (
          <div key={c.name} className="card p-4">
            <div className="flex items-center justify-between">
              <h2 className="font-medium text-gray-900">{c.name}</h2>
              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[c.status]}`}>
                {STATUS_LABELS[c.status] ?? c.status}
              </span>
            </div>
            <p className="text-sm text-gray-600 mt-2">{c.detail}</p>
          </div>
        ))}
      </section>

      <section className="grid md:grid-cols-4 gap-4" aria-label="Nyckeltal">
        {[
          ['Anrop (denna instans)', data.requests.count.toLocaleString('sv-SE')],
          ['Serverfel', data.requests.serverErrors.toLocaleString('sv-SE')],
          ['Svarstid: medel sedan start / p95 senaste minuterna', `${Math.round(data.requests.meanMs)} / ${Math.round(data.requests.p95Ms)} ms`],
          ['Minne', `${data.heapUsedMb} av ${data.heapMaxMb} MB`],
        ].map(([label, value]) => (
          <div key={label} className="card p-4">
            <div className="text-sm text-gray-500">{label}</div>
            <div className="text-xl font-semibold text-gray-900 mt-1 tabular-nums">{value}</div>
          </div>
        ))}
      </section>

      <div className="grid md:grid-cols-2 gap-6">
        <section className="card" aria-labelledby="instance-heading">
          <h2 id="instance-heading" className="px-6 py-4 border-b font-semibold text-gray-900">Instans</h2>
          <dl className="p-6 space-y-2 text-sm">
            <div className="flex justify-between"><dt className="text-gray-500">Startad</dt><dd>{formatTime(data.instanceStartedAt)}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Drifttid</dt><dd>{formatUptime(data.uptimeSeconds)}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Profil</dt><dd>{data.profiles || '–'}</dd></div>
          </dl>
          <h3 className="px-6 text-sm font-medium text-gray-700">Senaste starter (kallstarter)</h3>
          <ul className="px-6 pb-6 pt-2 space-y-1 text-sm">
            {data.recentStartups.map((s) => (
              <li key={s.id} className="flex justify-between">
                <span className="text-gray-600">{formatTime(s.timestamp)}</span>
                <span className="tabular-nums">{String(s.details.startupMs ?? '–')} ms</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="card" aria-labelledby="jobs-heading">
          <h2 id="jobs-heading" className="px-6 py-4 border-b font-semibold text-gray-900">Schemalagda jobb</h2>
          {data.lastJobRuns.length === 0 ? (
            <p className="p-6 text-sm text-gray-500">Inga körningar registrerade än.</p>
          ) : (
            <ul className="divide-y text-sm">
              {data.lastJobRuns.map((j) => (
                <li key={j.id} className="px-6 py-3">
                  <div className="flex justify-between">
                    <span className="font-medium">{j.source.replace('job:', '')}</span>
                    <span className={j.level === 'ERROR' ? 'text-red-700' : 'text-gray-600'}>{formatTime(j.timestamp)}</span>
                  </div>
                  <div className="text-gray-600">{j.message}</div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
