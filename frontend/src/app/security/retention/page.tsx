'use client';

import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getRetention, runRetention } from '@/lib/api/security';
import { toast } from '@/hooks/useToast';

export default function RetentionPage() {
  const queryClient = useQueryClient();
  const [running, setRunning] = useState(false);
  const { data, isLoading } = useQuery({ queryKey: ['retention'], queryFn: getRetention });

  const run = async () => {
    setRunning(true);
    try {
      const r = await runRetention();
      toast.success('Gallring klar',
        `${r.casesPurged} ärenden, ${r.draftsPurged} utkast och ${r.auditEntriesPurged} loggposter gallrades.`);
      await queryClient.invalidateQueries({ queryKey: ['retention'] });
    } catch {
      toast.error('Gallringen misslyckades');
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
        <div>
          <h1 className="page-title">Gallring</h1>
          <p className="page-lead">
            Gallring sker automatiskt varje natt. Varje gallrat ärende loggas i spårbarhetsloggen.
          </p>
        </div>
        <button type="button" onClick={run} disabled={running}
                className="btn-primary">
          {running ? 'Gallrar…' : 'Kör gallring nu'}
        </button>
      </div>

      <section className="grid md:grid-cols-3 gap-4" aria-label="Regler">
        <div className="card p-4">
          <h2 className="text-sm text-gray-500">Inskickade ärenden</h2>
          <p className="mt-1 text-gray-900">Enligt varje e-tjänsts gallringsfrist, räknat från avslut. Utan frist bevaras ärendena.</p>
        </div>
        <div className="card p-4">
          <h2 className="text-sm text-gray-500">Utkast</h2>
          <p className="mt-1 text-gray-900">{data ? `${data.draftDays} dagar` : '–'} efter senaste ändring</p>
        </div>
        <div className="card p-4">
          <h2 className="text-sm text-gray-500">Spårbarhetslogg</h2>
          <p className="mt-1 text-gray-900">{data ? `${data.auditMonths} månader` : '–'}</p>
        </div>
      </section>

      <section className="card" aria-labelledby="due-heading">
        <h2 id="due-heading" className="px-6 py-4 border-b text-lg font-semibold text-gray-900">
          Gallras inom 30 dagar
        </h2>
        {isLoading ? (
          <p className="p-6 text-gray-500">Laddar…</p>
        ) : !data || data.dueWithin30Days.length === 0 ? (
          <p className="p-6 text-gray-500">Inga ärenden gallras de närmaste 30 dagarna.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="table-head">
              <tr>
                <th scope="col" className="th px-6">Ärende</th>
                <th scope="col" className="th px-6">E-tjänst</th>
                <th scope="col" className="th px-6">Gallras</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.dueWithin30Days.map((c) => (
                <tr key={c.caseId}>
                  <td className="px-6 py-2 font-mono">{c.referenceNumber}</td>
                  <td className="px-6 py-2">{c.flowName}</td>
                  <td className="px-6 py-2">{new Date(c.purgeAfter).toLocaleDateString('sv-SE')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
