'use client';

import { useParams, useRouter } from 'next/navigation';
import { useState, useCallback } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import Link from 'next/link';
import { api } from '@/lib/api/client';
import { createCase, updateCaseValues, submitCase as submitCaseApi, type CaseDetail } from '@/lib/api/cases';
import { Header } from '@/components/layout';
import { useAuth } from '@/context/AuthContext';
import { FormRenderer, type Flow, type FormValues } from '@/components/form';

export default function FlowFormPage() {
  const params = useParams();
  const router = useRouter();
  const flowId = params.flowId as string;
  const { isAuthenticated, isLoading: authLoading, user } = useAuth();
  const [currentCase, setCurrentCase] = useState<CaseDetail | null>(null);
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');

  const { data: flow, isLoading, error } = useQuery({
    queryKey: ['flow', flowId],
    queryFn: async () => {
      const response = await api.get<Flow>(`/api/v1/flows/${flowId}`);
      return response;
    },
    enabled: !!flowId,
  });

  // Create a new case when user starts filling the form
  const createCaseMutation = useMutation({
    mutationFn: async () => {
      if (!user?.id) throw new Error('User not authenticated');
      return createCase(flowId, user.id);
    },
    onSuccess: (data) => {
      setCurrentCase(data);
    },
  });

  // Update case values
  const updateValuesMutation = useMutation({
    mutationFn: async (values: FormValues) => {
      if (!currentCase?.id) throw new Error('No case to update');
      // Convert string keys to UUID format for backend
      return updateCaseValues(currentCase.id, values);
    },
    onSuccess: (data) => {
      setCurrentCase(data);
      setSaveStatus('saved');
      setTimeout(() => setSaveStatus('idle'), 2000);
    },
    onError: () => {
      setSaveStatus('error');
    },
  });

  // Submit the case
  const submitMutation = useMutation({
    mutationFn: async (values: FormValues) => {
      // First ensure we have a case
      let caseId = currentCase?.id;
      if (!caseId) {
        if (!user?.id) throw new Error('User not authenticated');
        const newCase = await createCase(flowId, user.id);
        caseId = newCase.id;
        setCurrentCase(newCase);
      }

      // Update values if needed
      await updateCaseValues(caseId, values);

      // Submit the case
      return submitCaseApi(caseId);
    },
    onSuccess: (data) => {
      router.push(`/citizen/cases/${data.id}?submitted=true`);
    },
  });

  const handleSubmit = async (values: FormValues) => {
    await submitMutation.mutateAsync(values);
  };

  const handleSaveDraft = useCallback(async (values: FormValues) => {
    if (!isAuthenticated || !user?.id) return;

    setSaveStatus('saving');

    try {
      // Create case if it doesn't exist
      if (!currentCase?.id) {
        const newCase = await createCaseMutation.mutateAsync();
        await updateCaseValues(newCase.id, values);
        setCurrentCase(newCase);
        setSaveStatus('saved');
        setTimeout(() => setSaveStatus('idle'), 2000);
      } else {
        await updateValuesMutation.mutateAsync(values);
      }
    } catch {
      setSaveStatus('error');
    }
  }, [isAuthenticated, user?.id, currentCase?.id, createCaseMutation, updateValuesMutation]);

  const handleCancel = () => {
    router.push('/citizen/services');
  };

  if (isLoading) {
    return (
      <main className="min-h-screen bg-gray-50">
        <Header />
        <div className="container mx-auto px-4 py-8">
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            <span className="ml-3 text-gray-600">Laddar formulär...</span>
          </div>
        </div>
      </main>
    );
  }

  if (error || !flow) {
    return (
      <main className="min-h-screen bg-gray-50">
        <Header />
        <div className="container mx-auto px-4 py-8">
          <div className="bg-red-50 border border-red-200 rounded-lg p-8 text-center">
            <svg
              className="w-12 h-12 text-red-500 mx-auto mb-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
            <h3 className="text-lg font-medium text-red-900 mb-2">
              Kunde inte ladda formuläret
            </h3>
            <p className="text-red-700 mb-4">
              Tjänsten kunde inte hittas eller är inte tillgänglig.
            </p>
            <Link
              href="/citizen/services"
              className="inline-flex items-center text-red-600 hover:text-red-800 font-medium"
            >
              <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
              </svg>
              Tillbaka till tjänster
            </Link>
          </div>
        </div>
      </main>
    );
  }

  // Check if auth is required
  if (flow.requireAuth && !authLoading && !isAuthenticated) {
    return (
      <main className="min-h-screen bg-gray-50">
        <Header />
        <div className="container mx-auto px-4 py-8">
          <div className="max-w-2xl mx-auto">
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-8 text-center">
              <svg
                className="w-12 h-12 text-blue-500 mx-auto mb-4"
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
              <h3 className="text-lg font-medium text-blue-900 mb-2">
                Inloggning krävs
              </h3>
              <p className="text-blue-700 mb-4">
                Denna tjänst kräver att du loggar in med BankID eller annan e-legitimation.
              </p>
              <Link
                href={`/auth/login?redirect=/citizen/services/${flowId}`}
                className="inline-flex items-center justify-center bg-blue-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-blue-700 transition-colors"
              >
                Logga in
              </Link>
            </div>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-gray-50">
      <Header />

      <div className="container mx-auto px-4 py-8">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-sm text-gray-500 mb-6">
          <Link href="/citizen/services" className="hover:text-blue-600">
            E-tjänster
          </Link>
          <span>/</span>
          <span className="text-gray-900">{flow.name}</span>
        </nav>

        {/* Title */}
        <div className="mb-8">
          <div className="flex items-center gap-3 mb-2">
            {flow.typeName && (
              <span className="text-xs font-medium text-blue-600 bg-blue-50 px-2 py-1 rounded">
                {flow.typeName}
              </span>
            )}
            {flow.categoryName && (
              <span className="text-xs text-gray-500">
                {flow.categoryName}
              </span>
            )}
          </div>
          <h1 className="text-3xl font-bold text-gray-900">{flow.name}</h1>
          {flow.shortDescription && (
            <p className="mt-2 text-gray-600">{flow.shortDescription}</p>
          )}
        </div>

        {/* Long description if exists */}
        {flow.longDescription && (
          <div className="bg-blue-50 border border-blue-100 rounded-lg p-4 mb-8">
            <p className="text-blue-800 whitespace-pre-wrap">{flow.longDescription}</p>
          </div>
        )}

        {/* Form */}
        {flow.steps && flow.steps.length > 0 ? (
          <FormRenderer
            flow={flow}
            onSubmit={handleSubmit}
            onSaveDraft={isAuthenticated ? handleSaveDraft : undefined}
            onCancel={handleCancel}
            userId={user?.id}
            caseId={currentCase?.id}
          />
        ) : (
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-8 text-center">
            <p className="text-yellow-800">
              Denna tjänst har inga formulärsteg konfigurerade ännu.
            </p>
          </div>
        )}

        {/* Submit error */}
        {submitMutation.isError && (
          <div className="mt-4 bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800">
              Något gick fel vid inskickning. Vänligen försök igen.
            </p>
          </div>
        )}
      </div>
    </main>
  );
}
