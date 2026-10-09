'use client';

import Link from 'next/link';
import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import {
  ACTION_LABELS,
  CATEGORY_LABELS,
  OUTCOME_LABELS,
  exportAudit,
  searchAudit,
  verifyAudit,
  type AuditCategory,
  type AuditFilter,
  type AuditOutcome,
  type ChainResult,
} from '@/lib/api/security';
import { toast } from '@/hooks/useToast';

const OUTCOME_STYLES: Record<AuditOutcome, string> = {
  SUCCESS: 'bg-green-100 text-green-800',
  DENIED: 'bg-amber-100 text-amber-800',
  FAILURE: 'bg-red-100 text-red-800',
};

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('sv-SE', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

export default function AuditPage() {
  return (
    <Suspense fallback={null}>
      <AuditLog />
    </Suspense>
  );
}

function AuditLog() {
  const params = useSearchParams();
  const initial: AuditFilter = {
    subjectId: params.get('subjectId') ?? '',
    userId: params.get('userId') ?? '',
  };
  const [draft, setDraft] = useState<AuditFilter>(initial);
  const [filter, setFilter] = useState<AuditFilter>(initial);
  const [page, setPage] = useState(0);
  const [chain, setChain] = useState<ChainResult | null>(null);
  const [checking, setChecking] = useState(false);

  const { data, isLoading, error } = useQuery({
    queryKey: ['audit', filter, page],
    queryFn: () => searchAudit(filter, page),
  });

  const apply = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setFilter(draft);
  };

  const reset = () => {
    setDraft({});
    setFilter({});
    setPage(0);
  };

  const check = async () => {
    setChecking(true);
    try {
      setChain(await verifyAudit());
    } catch {
      toast.error('Kontrollen kunde inte genomföras');
    } finally {
      setChecking(false);
    }
  };

  const doExport = async (format: 'csv' | 'json') => {
    try {
      await exportAudit(filter, format);
    } catch {
      toast.error('Exporten misslyckades');
    }
  };

  const field = 'w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-slate-500';

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Spårbarhetslogg</h1>
          <p className="text-gray-600 mt-1">
            Vem som har läst eller ändrat vilka uppgifter, och när. Loggen kan inte ändras i efterhand.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={check}
            disabled={checking}
            className="px-4 py-2 text-sm bg-white border rounded-lg hover:bg-gray-50 disabled:opacity-50"
          >
            {checking ? 'Kontrollerar…' : 'Kontrollera loggens integritet'}
          </button>
          <button type="button" onClick={() => doExport('csv')} className="px-4 py-2 text-sm bg-slate-800 text-white rounded-lg hover:bg-slate-700">
            Exportera CSV
          </button>
          <button type="button" onClick={() => doExport('json')} className="px-4 py-2 text-sm bg-white border rounded-lg hover:bg-gray-50">
            JSON
          </button>
        </div>
      </div>

      {chain && (
        <div
          role="status"
          className={`p-4 rounded-lg border text-sm ${
            chain.intact ? 'bg-green-50 border-green-200 text-green-900' : 'bg-red-50 border-red-200 text-red-900'
          }`}
        >
          {chain.intact
            ? `Loggen är intakt: alla ${chain.verified} poster stämmer mot sina kontrollsummor.`
            : `Loggen har ändrats! Post nr ${chain.firstBrokenSeq} stämmer inte mot kontrollsumman. ${chain.verified} poster före den är intakta.`}
        </div>
      )}

      <form onSubmit={apply} className="bg-white rounded-lg border p-4 grid grid-cols-1 md:grid-cols-4 gap-3">
        <div>
          <label htmlFor="f-category" className="block text-xs font-medium text-gray-600 mb-1">Kategori</label>
          <select id="f-category" className={field} value={draft.category ?? ''}
                  onChange={(e) => setDraft({ ...draft, category: e.target.value as AuditCategory | '' })}>
            <option value="">Alla</option>
            {Object.entries(CATEGORY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="f-action" className="block text-xs font-medium text-gray-600 mb-1">Händelse</label>
          <select id="f-action" className={field} value={draft.action ?? ''}
                  onChange={(e) => setDraft({ ...draft, action: e.target.value })}>
            <option value="">Alla</option>
            {Object.entries(ACTION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="f-outcome" className="block text-xs font-medium text-gray-600 mb-1">Utfall</label>
          <select id="f-outcome" className={field} value={draft.outcome ?? ''}
                  onChange={(e) => setDraft({ ...draft, outcome: e.target.value as AuditOutcome | '' })}>
            <option value="">Alla</option>
            {Object.entries(OUTCOME_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="f-subject" className="block text-xs font-medium text-gray-600 mb-1">Registrerad (id)</label>
          <input id="f-subject" className={field} value={draft.subjectId ?? ''}
                 onChange={(e) => setDraft({ ...draft, subjectId: e.target.value })} />
        </div>
        <div>
          <label htmlFor="f-user" className="block text-xs font-medium text-gray-600 mb-1">Utförd av (id)</label>
          <input id="f-user" className={field} value={draft.userId ?? ''}
                 onChange={(e) => setDraft({ ...draft, userId: e.target.value })} />
        </div>
        <div>
          <label htmlFor="f-from" className="block text-xs font-medium text-gray-600 mb-1">Från</label>
          <input id="f-from" type="date" className={field} value={draft.from ?? ''}
                 onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
        </div>
        <div>
          <label htmlFor="f-to" className="block text-xs font-medium text-gray-600 mb-1">Till</label>
          <input id="f-to" type="date" className={field} value={draft.to ?? ''}
                 onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
        </div>
        <div className="flex items-end gap-2">
          <button type="submit" className="flex-1 px-4 py-2 text-sm bg-slate-800 text-white rounded-lg hover:bg-slate-700">
            Sök
          </button>
          <button type="button" onClick={reset} className="px-3 py-2 text-sm text-gray-600 hover:text-gray-900">
            Rensa
          </button>
        </div>
      </form>

      <div className="bg-white rounded-lg border overflow-hidden">
        {isLoading ? (
          <p className="p-6 text-gray-500">Laddar…</p>
        ) : error ? (
          <p className="p-6 text-red-700" role="alert">Loggen kunde inte hämtas.</p>
        ) : data && data.content.length === 0 ? (
          <p className="p-6 text-gray-500">Inga poster matchar filtret.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  {['Nr', 'Tid', 'Utförd av', 'Händelse', 'Utfall', 'Objekt', 'Registrerad', 'Detaljer'].map((h) => (
                    <th key={h} scope="col" className="text-left px-4 py-2 text-xs font-medium text-gray-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y">
                {data?.content.map((e) => (
                  <tr key={e.seq} className="align-top">
                    <td className="px-4 py-2 text-gray-500 tabular-nums">{e.seq}</td>
                    <td className="px-4 py-2 whitespace-nowrap tabular-nums">{formatTime(e.timestamp)}</td>
                    <td className="px-4 py-2">
                      <div className="text-gray-900">{e.userName ?? (e.userId === 'anonymous' ? 'Ej inloggad' : e.userId === 'system' ? 'Systemet' : e.userId)}</div>
                      {e.ipAddress && <div className="text-xs text-gray-500">{e.ipAddress}</div>}
                    </td>
                    <td className="px-4 py-2">
                      <div>{ACTION_LABELS[e.action] ?? e.action}</div>
                      <div className="text-xs text-gray-500">{CATEGORY_LABELS[e.category]}</div>
                    </td>
                    <td className="px-4 py-2">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${OUTCOME_STYLES[e.outcome]}`}>
                        {OUTCOME_LABELS[e.outcome]}{e.responseStatus && e.outcome !== 'SUCCESS' ? ` (${e.responseStatus})` : ''}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-xs text-gray-600">
                      {e.entityType && <div>{e.entityType}</div>}
                      {e.entityId && <div className="font-mono break-all">{e.entityId}</div>}
                    </td>
                    <td className="px-4 py-2 text-xs">
                      {e.subjectUserId && (
                        <Link href={`/security/people?id=${e.subjectUserId}`} className="font-mono text-slate-700 hover:underline break-all">
                          {e.subjectUserId.slice(0, 8)}…
                        </Link>
                      )}
                    </td>
                    <td className="px-4 py-2 text-xs text-gray-600 max-w-xs">{e.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-600">{data.totalElements} poster</span>
          <div className="flex gap-2">
            <button type="button" disabled={data.first} onClick={() => setPage((p) => p - 1)}
                    className="px-3 py-1 border rounded disabled:opacity-40">Föregående</button>
            <span className="px-2 py-1">Sida {data.number + 1} av {data.totalPages}</span>
            <button type="button" disabled={data.last} onClick={() => setPage((p) => p + 1)}
                    className="px-3 py-1 border rounded disabled:opacity-40">Nästa</button>
          </div>
        </div>
      )}
    </div>
  );
}
