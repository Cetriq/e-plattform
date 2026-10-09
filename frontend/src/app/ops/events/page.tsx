'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getSystemEvents, type EventLevel } from '@/lib/api/ops';

const LEVEL_STYLES: Record<EventLevel, string> = {
  INFO: 'bg-blue-100 text-blue-800',
  WARN: 'bg-amber-100 text-amber-800',
  ERROR: 'bg-red-100 text-red-800',
};

export default function SystemLogPage() {
  const [level, setLevel] = useState<EventLevel | ''>('');
  const [source, setSource] = useState('');
  const [page, setPage] = useState(0);
  const { data, isLoading } = useQuery({
    queryKey: ['system-events', level, source, page],
    queryFn: () => getSystemEvents(level, source, page),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Systemlogg</h1>
        <p className="text-gray-600 mt-1">Tekniska händelser: fel, jobbkörningar och starter. Innehåller inga personuppgifter.</p>
      </div>

      <div className="flex flex-wrap gap-3">
        <div>
          <label htmlFor="level" className="block text-xs font-medium text-gray-600 mb-1">Nivå</label>
          <select id="level" value={level} onChange={(e) => { setLevel(e.target.value as EventLevel | ''); setPage(0); }}
                  className="px-3 py-2 border rounded-lg text-sm">
            <option value="">Alla</option>
            <option value="ERROR">Fel</option>
            <option value="WARN">Varning</option>
            <option value="INFO">Info</option>
          </select>
        </div>
        <div>
          <label htmlFor="source" className="block text-xs font-medium text-gray-600 mb-1">Källa</label>
          <select id="source" value={source} onChange={(e) => { setSource(e.target.value); setPage(0); }}
                  className="px-3 py-2 border rounded-lg text-sm">
            <option value="">Alla</option>
            <option value="api">API</option>
            <option value="job:">Jobb</option>
            <option value="app">Start</option>
          </select>
        </div>
      </div>

      <div className="bg-white rounded-lg border overflow-hidden">
        {isLoading ? (
          <p className="p-6 text-gray-500">Laddar…</p>
        ) : !data || data.content.length === 0 ? (
          <p className="p-6 text-gray-500">Inga händelser.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                {['Tid', 'Nivå', 'Källa', 'Händelse', 'Detaljer'].map((h) => (
                  <th key={h} scope="col" className="text-left px-4 py-2 text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.content.map((e) => (
                <tr key={e.id} className="align-top">
                  <td className="px-4 py-2 whitespace-nowrap tabular-nums">{new Date(e.timestamp).toLocaleString('sv-SE')}</td>
                  <td className="px-4 py-2"><span className={`px-2 py-0.5 rounded-full text-xs font-medium ${LEVEL_STYLES[e.level]}`}>{e.level}</span></td>
                  <td className="px-4 py-2 font-mono text-xs">{e.source}</td>
                  <td className="px-4 py-2">{e.message}</td>
                  <td className="px-4 py-2 text-xs text-gray-600 font-mono break-all">
                    {Object.entries(e.details).map(([k, v]) => `${k}=${String(v)}`).join(' ')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex justify-end gap-2 text-sm">
          <button type="button" disabled={data.first} onClick={() => setPage((p) => p - 1)} className="px-3 py-1 border rounded disabled:opacity-40">Föregående</button>
          <span className="px-2 py-1">Sida {data.number + 1} av {data.totalPages}</span>
          <button type="button" disabled={data.last} onClick={() => setPage((p) => p + 1)} className="px-3 py-1 border rounded disabled:opacity-40">Nästa</button>
        </div>
      )}
    </div>
  );
}
