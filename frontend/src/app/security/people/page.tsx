'use client';

import Link from 'next/link';
import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  downloadRegisterExtract,
  erasePerson,
  getErasurePreview,
  searchPeople,
  type PersonSummary,
} from '@/lib/api/security';
import { toast } from '@/hooks/useToast';

const ROLE_LABELS: Record<string, string> = {
  USER: 'Medborgare',
  MANAGER: 'Handläggare',
  ADMIN: 'Administratör',
  FLOW_EDITOR: 'E-tjänstredaktör',
  SECURITY_OFFICER: 'Informationssäkerhet',
  OPERATIONS: 'IT & drift',
};

function formatDate(iso?: string) {
  return iso ? new Date(iso).toLocaleDateString('sv-SE') : '–';
}

export default function PeoplePage() {
  return (
    <Suspense fallback={null}>
      <People />
    </Suspense>
  );
}

function People() {
  const params = useSearchParams();
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(params.get('id'));

  const results = useQuery({
    queryKey: ['people', submitted],
    queryFn: () => searchPeople(submitted),
    enabled: submitted.length >= 2,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="page-title">Registrerade</h1>
        <p className="page-lead">
          Registerutdrag och begäran om radering. Allt du gör här loggas i spårbarhetsloggen.
        </p>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setSubmitted(query.trim());
        }}
        className="flex gap-2 max-w-xl"
      >
        <label htmlFor="person-search" className="sr-only">Sök på namn eller e-post</label>
        <input
          id="person-search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Sök på namn eller e-post…"
          className="input flex-1"
        />
        <button type="submit" className="btn-primary">Sök</button>
      </form>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="card">
          {submitted.length < 2 ? (
            <p className="p-4 text-sm text-gray-500">Sök för att hitta en person.</p>
          ) : results.isLoading ? (
            <p className="p-4 text-sm text-gray-500">Söker…</p>
          ) : (results.data ?? []).length === 0 ? (
            <p className="p-4 text-sm text-gray-500">Ingen träff.</p>
          ) : (
            <ul className="divide-y">
              {results.data!.map((p) => (
                <li key={p.id}>
                  <button
                    type="button"
                    onClick={() => setSelectedId(p.id)}
                    aria-current={selectedId === p.id ? 'true' : undefined}
                    className={`w-full text-left px-4 py-3 hover:bg-gray-50 ${selectedId === p.id ? 'bg-brand-50' : ''}`}
                  >
                    <div className="font-medium text-gray-900">{p.name}</div>
                    <div className="text-sm text-gray-500">{p.email}</div>
                    <div className="text-xs text-gray-500 mt-1">
                      {p.roles.map((r) => ROLE_LABELS[r] ?? r).join(', ') || 'Ingen roll'}
                      {!p.active && ' · avaktiverad'}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="lg:col-span-2">
          {selectedId ? (
            <PersonDetail userId={selectedId} />
          ) : (
            <div className="card p-6 text-sm text-gray-500">Välj en person.</div>
          )}
        </div>
      </div>
    </div>
  );
}

function PersonDetail({ userId }: { userId: string }) {
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const [erasing, setErasing] = useState(false);

  const { data, isLoading, error } = useQuery({
    queryKey: ['erasure', userId],
    queryFn: () => getErasurePreview(userId),
  });

  if (isLoading) return <div className="card p-6 text-gray-500">Laddar…</div>;
  if (error || !data) return <div className="card p-6 text-red-700" role="alert">Personen kunde inte hämtas.</div>;

  const person: PersonSummary = data.person;

  const extract = async () => {
    try {
      await downloadRegisterExtract(person);
      toast.success('Registerutdrag nedladdat');
    } catch {
      toast.error('Registerutdraget kunde inte tas fram');
    }
  };

  const erase = async () => {
    setErasing(true);
    try {
      const result = await erasePerson(userId);
      toast.success('Personuppgifter raderade',
        `${result.draftsDeleted} utkast raderade, ${result.casesRetained} ärenden bevaras.`);
      setConfirming(false);
      await queryClient.invalidateQueries({ queryKey: ['erasure', userId] });
      await queryClient.invalidateQueries({ queryKey: ['people'] });
    } catch (err) {
      const message = err && typeof err === 'object' && 'message' in err ? String(err.message) : '';
      toast.error('Raderingen misslyckades', message);
    } finally {
      setErasing(false);
    }
  };

  return (
    <div className="space-y-6">
      <section className="card p-6">
        <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">{person.name}</h2>
            <p className="text-sm text-gray-600">{person.email}</p>
            <p className="text-sm text-gray-500 mt-1">
              {person.submittedCases} inskickade ärenden · {person.drafts} utkast
              {!person.active && ' · kontot är avaktiverat'}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={extract} className="btn-primary">
              Ta fram registerutdrag
            </button>
            <Link href={`/security/audit?subjectId=${person.id}`} className="btn-secondary">
              Vem har läst uppgifterna?
            </Link>
          </div>
        </div>
        <p className="text-xs text-gray-500 mt-4">
          Registerutdraget innehåller allt systemet har om personen, även interna anteckningar.
          Pröva sekretess enligt OSL innan utdraget lämnas ut.
        </p>
      </section>

      <section className="card p-6 space-y-4" aria-labelledby="erasure-heading">
        <h2 id="erasure-heading" className="text-lg font-semibold text-gray-900">Begäran om radering</h2>

        {!data.allowed ? (
          <p className="text-sm text-gray-700">{data.notAllowedReason}</p>
        ) : (
          <>
            <div className="grid md:grid-cols-2 gap-4 text-sm">
              <div className="p-4 rounded-lg bg-red-50 border border-red-100">
                <h3 className="font-medium text-red-900 mb-2">Raderas</h3>
                <ul className="list-disc pl-5 space-y-1 text-red-900">
                  <li>{data.draftsToDelete} utkast som aldrig skickats in</li>
                  <li>Namn, e-post och telefonnummer</li>
                  <li>Kontot avaktiveras och kan inte längre logga in</li>
                </ul>
              </div>
              <div className="p-4 rounded-lg bg-gray-50 border">
                <h3 className="font-medium text-gray-900 mb-2">Bevaras ({data.retainedCases.length})</h3>
                {data.retainedCases.length === 0 ? (
                  <p className="text-gray-600">Inga inskickade ärenden.</p>
                ) : (
                  <ul className="space-y-1 text-gray-700">
                    {data.retainedCases.map((c) => (
                      <li key={c.referenceNumber}>
                        <span className="font-mono">{c.referenceNumber}</span> {c.flowName}
                        <span className="block text-xs text-gray-500">
                          {c.purgeAfter
                            ? `Gallras ${formatDate(c.purgeAfter)}`
                            : c.retentionMonths
                              ? `Gallras ${c.retentionMonths} mån efter avslut`
                              : 'Bevaras enligt arkivlagen'}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
            <p className="text-xs text-gray-500">
              Inskickade ärenden är allmänna handlingar. De undantas från rätten till radering
              (artikel 17.3 b dataskyddsförordningen) och gallras enligt e-tjänstens gallringsfrist.
            </p>

            {!confirming ? (
              <button type="button" onClick={() => setConfirming(true)}
                      className="btn-danger-outline">
                Radera personuppgifter…
              </button>
            ) : (
              <div className="p-4 border border-red-300 rounded-lg bg-red-50 space-y-3" role="alertdialog" aria-labelledby="confirm-erase">
                <p id="confirm-erase" className="text-sm text-red-900">
                  Raderingen kan inte ångras. Vill du radera {person.name}s uppgifter enligt ovan?
                </p>
                <div className="flex gap-2">
                  <button type="button" onClick={erase} disabled={erasing}
                          className="btn-danger">
                    {erasing ? 'Raderar…' : 'Ja, radera'}
                  </button>
                  <button type="button" onClick={() => setConfirming(false)}
                          className="btn-secondary">
                    Avbryt
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
