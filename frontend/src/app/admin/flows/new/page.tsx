'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { createFlow, type CreateFlowRequest } from '@/lib/api/admin';

export default function NewFlowPage() {
  const router = useRouter();
  const [name, setName] = useState('');
  const [shortDescription, setShortDescription] = useState('');
  const [requireAuth, setRequireAuth] = useState(true);

  const createMutation = useMutation({
    mutationFn: (request: CreateFlowRequest) => createFlow(request),
    onSuccess: (flow) => {
      router.push(`/admin/flows/${flow.id}`);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate({
      name,
      shortDescription: shortDescription || undefined,
      requireAuth,
    });
  };

  return (
    <div className="max-w-2xl mx-auto">
      {/* Header */}
      <div className="flex items-center gap-4 mb-8">
        <Link
          href="/admin/flows"
          className="text-gray-500 hover:text-gray-700"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Skapa ny e-tjänst</h1>
          <p className="text-gray-600">
            Börja med att ge din e-tjänst ett namn och en kort beskrivning.
          </p>
        </div>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="bg-white rounded-lg border shadow-sm p-6 space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Namn på e-tjänsten *
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            autoFocus
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            placeholder="t.ex. Ansökan om bygglov"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Kort beskrivning
          </label>
          <input
            type="text"
            value={shortDescription}
            onChange={(e) => setShortDescription(e.target.value)}
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            placeholder="Visas i listningen av e-tjänster"
          />
        </div>

        <div className="border-t pt-6">
          <h3 className="font-medium text-gray-900 mb-4">Grundinställningar</h3>
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={requireAuth}
              onChange={(e) => setRequireAuth(e.target.checked)}
              className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
            />
            <div>
              <span className="text-sm font-medium text-gray-700">Kräv inloggning</span>
              <p className="text-xs text-gray-500">Användare måste logga in för att använda e-tjänsten</p>
            </div>
          </label>
        </div>

        {createMutation.isError && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800 text-sm">
              Kunde inte skapa e-tjänsten. Försök igen.
            </p>
          </div>
        )}

        <div className="flex justify-end gap-3 pt-4">
          <Link
            href="/admin/flows"
            className="px-4 py-2 border rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Avbryt
          </Link>
          <button
            type="submit"
            disabled={createMutation.isPending || !name.trim()}
            className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50"
          >
            {createMutation.isPending ? 'Skapar...' : 'Skapa e-tjänst'}
          </button>
        </div>
      </form>

      {/* Tips */}
      <div className="mt-8 bg-purple-50 rounded-lg p-6">
        <h3 className="font-medium text-purple-900 mb-2">Tips för att komma igång</h3>
        <ul className="text-sm text-purple-700 space-y-2">
          <li className="flex items-start gap-2">
            <svg className="w-5 h-5 text-purple-500 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Ge e-tjänsten ett tydligt och beskrivande namn</span>
          </li>
          <li className="flex items-start gap-2">
            <svg className="w-5 h-5 text-purple-500 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Dela upp formuläret i logiska steg för bättre användarupplevelse</span>
          </li>
          <li className="flex items-start gap-2">
            <svg className="w-5 h-5 text-purple-500 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Testa formuläret med förhandsgranskning innan publicering</span>
          </li>
        </ul>
      </div>
    </div>
  );
}
