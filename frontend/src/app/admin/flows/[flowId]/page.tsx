'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getFlowForEdit,
  updateFlow,
  publishFlow,
  addStep,
  updateStep,
  deleteStep,
  addQueryDefinition,
  updateQueryDefinition,
  deleteQueryDefinition,
  getFlowTypes,
  type FlowDetail,
  type StepDetail,
  type StepInput,
  type QueryDefinitionInput,
  type QueryDefinitionDetail,
  type FlowType,
  type Category,
} from '@/lib/api/admin';

// Query types available - grouped by category
const queryTypes = [
  // Text inputs
  { value: 'TEXT', label: 'Textfält', icon: 'A', category: 'text' },
  { value: 'TEXTAREA', label: 'Flerradigt textfält', icon: '¶', category: 'text' },
  { value: 'NUMBER', label: 'Nummer', icon: '#', category: 'text' },
  { value: 'EMAIL', label: 'E-post', icon: '@', category: 'text' },
  { value: 'PHONE', label: 'Telefon', icon: '☎', category: 'text' },
  { value: 'URL', label: 'Webbadress', icon: '🔗', category: 'text' },
  // Date/Time
  { value: 'DATE', label: 'Datum', icon: '📅', category: 'datetime' },
  { value: 'DATETIME', label: 'Datum och tid', icon: '📅', category: 'datetime' },
  { value: 'TIME', label: 'Tid', icon: '🕐', category: 'datetime' },
  // Selection
  { value: 'SELECT', label: 'Rullgardinsmeny', icon: '▼', category: 'selection' },
  { value: 'MULTISELECT', label: 'Flerval lista', icon: '☑', category: 'selection' },
  { value: 'RADIO', label: 'Radioknappar', icon: '◉', category: 'selection' },
  { value: 'CHECKBOX', label: 'Kryssrutor', icon: '☑', category: 'selection' },
  // Files
  { value: 'FILE', label: 'Filuppladdning', icon: '📎', category: 'file' },
  { value: 'IMAGE', label: 'Bilduppladdning', icon: '🖼', category: 'file' },
  // Special
  { value: 'PERSON', label: 'Personuppgifter', icon: '👤', category: 'special' },
  { value: 'ORGANIZATION', label: 'Organisationsuppgifter', icon: '🏢', category: 'special' },
  { value: 'LOCATION', label: 'Platsväljare', icon: '📍', category: 'special' },
  { value: 'MAP', label: 'Kartmarkering', icon: '🗺', category: 'special' },
  { value: 'SIGNATURE', label: 'Signatur', icon: '✍', category: 'special' },
  // Layout elements (non-input)
  { value: 'HEADING', label: 'Rubrik', icon: 'H', category: 'layout' },
  { value: 'PARAGRAPH', label: 'Text/Information', icon: 'P', category: 'layout' },
  { value: 'DIVIDER', label: 'Avdelare', icon: '—', category: 'layout' },
];

type Tab = 'steps' | 'settings' | 'preview';

export default function FlowEditorPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const flowId = params.flowId as string;
  const isNew = flowId === 'new';

  const [activeTab, setActiveTab] = useState<Tab>('steps');
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);
  const [editingQuery, setEditingQuery] = useState<QueryDefinitionDetail | null>(null);
  const [showAddQueryModal, setShowAddQueryModal] = useState(false);
  const [showStepModal, setShowStepModal] = useState(false);
  const [editingStep, setEditingStep] = useState<StepDetail | null>(null);

  // Flow data
  const { data: flow, isLoading, error } = useQuery({
    queryKey: ['admin-flow', flowId],
    queryFn: () => getFlowForEdit(flowId),
    enabled: !isNew,
  });

  // Flow types for category selection
  const { data: flowTypes } = useQuery({
    queryKey: ['flow-types'],
    queryFn: getFlowTypes,
  });

  // Form state for flow settings
  const [flowForm, setFlowForm] = useState({
    name: '',
    shortDescription: '',
    longDescription: '',
    submittedMessage: '',
    typeId: '' as string | undefined,
    categoryId: '' as string | undefined,
    requireAuth: true,
    requireSigning: false,
    allowSaveDraft: true,
    allowMultiple: true,
    enabled: false,
  });

  // Update form when flow data loads
  useEffect(() => {
    if (flow) {
      setFlowForm({
        name: flow.name,
        shortDescription: flow.shortDescription || '',
        longDescription: flow.longDescription || '',
        submittedMessage: flow.submittedMessage || '',
        typeId: flow.typeId || '',
        categoryId: flow.categoryId || '',
        requireAuth: flow.requireAuth,
        requireSigning: flow.requireSigning,
        allowSaveDraft: flow.allowSaveDraft,
        allowMultiple: flow.allowMultiple,
        enabled: flow.enabled,
      });
      if (flow.steps.length > 0 && !selectedStepId) {
        setSelectedStepId(flow.steps[0].id);
      }
    }
  }, [flow]);

  // Mutations
  const updateFlowMutation = useMutation({
    mutationFn: (data: typeof flowForm) => updateFlow(flowId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
    },
  });

  const publishMutation = useMutation({
    mutationFn: () => publishFlow(flowId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
    },
  });

  const addStepMutation = useMutation({
    mutationFn: (step: StepInput) => addStep(flowId, step),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
      setShowStepModal(false);
    },
  });

  const updateStepMutation = useMutation({
    mutationFn: ({ stepId, step }: { stepId: string; step: StepInput }) =>
      updateStep(flowId, stepId, step),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
      setShowStepModal(false);
      setEditingStep(null);
    },
  });

  const deleteStepMutation = useMutation({
    mutationFn: (stepId: string) => deleteStep(flowId, stepId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
      setSelectedStepId(null);
    },
  });

  const addQueryMutation = useMutation({
    mutationFn: (query: QueryDefinitionInput) =>
      addQueryDefinition(flowId, selectedStepId!, query),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
      setShowAddQueryModal(false);
    },
  });

  const updateQueryMutation = useMutation({
    mutationFn: ({ queryId, query }: { queryId: string; query: QueryDefinitionInput }) =>
      updateQueryDefinition(flowId, selectedStepId!, queryId, query),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
      setEditingQuery(null);
    },
  });

  const deleteQueryMutation = useMutation({
    mutationFn: (queryId: string) =>
      deleteQueryDefinition(flowId, selectedStepId!, queryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-flow', flowId] });
    },
  });

  const selectedStep = flow?.steps.find((s) => s.id === selectedStepId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600"></div>
        <span className="ml-3 text-gray-600">Laddar e-tjänst...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4">
        <p className="text-red-800">Kunde inte ladda e-tjänsten.</p>
        <Link href="/admin/flows" className="text-red-600 hover:underline mt-2 inline-block">
          Tillbaka till listan
        </Link>
      </div>
    );
  }

  return (
    <div className="h-full">
      {/* Header */}
      <div className="flex justify-between items-center mb-6">
        <div className="flex items-center gap-4">
          <Link
            href="/admin/flows"
            className="text-gray-500 hover:text-gray-700"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">
              {flow?.name || 'Ny e-tjänst'}
            </h1>
            <p className="text-sm text-gray-500">
              {flow?.status === 'DRAFT' && 'Utkast'}
              {flow?.status === 'PUBLISHED' && 'Publicerad'}
              {flow?.status === 'ARCHIVED' && 'Arkiverad'}
              {flow && ` • Version ${flow.version}`}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Link
            href={`/citizen/services/${flowId}`}
            className="px-4 py-2 border rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Förhandsgranska
          </Link>
          {flow?.status === 'DRAFT' && (
            <button
              onClick={() => publishMutation.mutate()}
              disabled={publishMutation.isPending}
              className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
            >
              {publishMutation.isPending ? 'Publicerar...' : 'Publicera'}
            </button>
          )}
          <button
            onClick={() => updateFlowMutation.mutate(flowForm)}
            disabled={updateFlowMutation.isPending}
            className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50"
          >
            {updateFlowMutation.isPending ? 'Sparar...' : 'Spara'}
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b mb-6">
        <nav className="flex gap-6">
          <button
            onClick={() => setActiveTab('steps')}
            className={`pb-3 text-sm font-medium border-b-2 -mb-px transition-colors ${
              activeTab === 'steps'
                ? 'border-purple-600 text-purple-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Formulärfält
          </button>
          <button
            onClick={() => setActiveTab('settings')}
            className={`pb-3 text-sm font-medium border-b-2 -mb-px transition-colors ${
              activeTab === 'settings'
                ? 'border-purple-600 text-purple-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Inställningar
          </button>
          <button
            onClick={() => setActiveTab('preview')}
            className={`pb-3 text-sm font-medium border-b-2 -mb-px transition-colors ${
              activeTab === 'preview'
                ? 'border-purple-600 text-purple-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Förhandsgranskning
          </button>
        </nav>
      </div>

      {/* Content */}
      {activeTab === 'steps' && (
        <div className="flex gap-6">
          {/* Steps sidebar */}
          <div className="w-64 flex-shrink-0">
            <div className="bg-white rounded-lg border shadow-sm">
              <div className="p-4 border-b">
                <div className="flex items-center justify-between">
                  <h3 className="font-medium text-gray-900">Steg</h3>
                  <button
                    onClick={() => {
                      setEditingStep(null);
                      setShowStepModal(true);
                    }}
                    className="p-1 text-purple-600 hover:bg-purple-50 rounded"
                    title="Lägg till steg"
                  >
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                  </button>
                </div>
              </div>
              <div className="p-2">
                {flow?.steps.length === 0 ? (
                  <p className="text-sm text-gray-500 p-2">
                    Inga steg ännu. Lägg till ett steg för att börja.
                  </p>
                ) : (
                  <div className="space-y-1">
                    {flow?.steps.map((step, index) => (
                      <button
                        key={step.id}
                        onClick={() => setSelectedStepId(step.id)}
                        className={`w-full text-left px-3 py-2 rounded-lg text-sm flex items-center gap-2 ${
                          selectedStepId === step.id
                            ? 'bg-purple-100 text-purple-700'
                            : 'hover:bg-gray-100 text-gray-700'
                        }`}
                      >
                        <span className="w-5 h-5 rounded-full bg-gray-200 flex items-center justify-center text-xs">
                          {index + 1}
                        </span>
                        <span className="truncate flex-1">{step.name}</span>
                        <span className="text-xs text-gray-400">
                          {step.queries.length}
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Queries editor */}
          <div className="flex-1">
            {selectedStep ? (
              <div className="bg-white rounded-lg border shadow-sm">
                <div className="p-4 border-b flex items-center justify-between">
                  <div>
                    <h3 className="font-medium text-gray-900">{selectedStep.name}</h3>
                    {selectedStep.description && (
                      <p className="text-sm text-gray-500">{selectedStep.description}</p>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => {
                        setEditingStep(selectedStep);
                        setShowStepModal(true);
                      }}
                      className="p-1 text-gray-400 hover:text-gray-600 rounded"
                      title="Redigera steg"
                    >
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                      </svg>
                    </button>
                    <button
                      onClick={() => {
                        if (confirm('Är du säker på att du vill ta bort detta steg?')) {
                          deleteStepMutation.mutate(selectedStep.id);
                        }
                      }}
                      className="p-1 text-red-400 hover:text-red-600 rounded"
                      title="Ta bort steg"
                    >
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </div>

                <div className="p-4">
                  {selectedStep.queries.length === 0 ? (
                    <div className="text-center py-8">
                      <svg
                        className="w-12 h-12 text-gray-300 mx-auto mb-3"
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
                      <p className="text-gray-500 mb-4">Inga fält i detta steg</p>
                      <button
                        onClick={() => setShowAddQueryModal(true)}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
                      >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Lägg till fält
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {selectedStep.queries.map((query) => (
                        <QueryCard
                          key={query.id}
                          query={query}
                          onEdit={() => setEditingQuery(query)}
                          onDelete={() => {
                            if (confirm('Ta bort detta fält?')) {
                              deleteQueryMutation.mutate(query.id);
                            }
                          }}
                        />
                      ))}
                      <button
                        onClick={() => setShowAddQueryModal(true)}
                        className="w-full py-3 border-2 border-dashed border-gray-300 rounded-lg text-gray-500 hover:border-purple-500 hover:text-purple-600 transition-colors flex items-center justify-center gap-2"
                      >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Lägg till fält
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="bg-white rounded-lg border shadow-sm p-8 text-center">
                <svg
                  className="w-12 h-12 text-gray-300 mx-auto mb-3"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M4 6h16M4 10h16M4 14h16M4 18h16"
                  />
                </svg>
                <p className="text-gray-500">
                  {flow?.steps.length === 0
                    ? 'Lägg till ett steg för att börja bygga formuläret'
                    : 'Välj ett steg i sidofältet för att redigera'}
                </p>
              </div>
            )}
          </div>
        </div>
      )}

      {activeTab === 'settings' && (
        <div className="max-w-2xl">
          <div className="bg-white rounded-lg border shadow-sm p-6 space-y-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Namn på e-tjänsten *
              </label>
              <input
                type="text"
                value={flowForm.name}
                onChange={(e) => setFlowForm({ ...flowForm, name: e.target.value })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Kort beskrivning
              </label>
              <input
                type="text"
                value={flowForm.shortDescription}
                onChange={(e) => setFlowForm({ ...flowForm, shortDescription: e.target.value })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder="Visas i listningen av e-tjänster"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Lång beskrivning
              </label>
              <textarea
                value={flowForm.longDescription}
                onChange={(e) => setFlowForm({ ...flowForm, longDescription: e.target.value })}
                rows={4}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder="Visas på e-tjänstens startsida"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Bekräftelsemeddelande
              </label>
              <textarea
                value={flowForm.submittedMessage}
                onChange={(e) => setFlowForm({ ...flowForm, submittedMessage: e.target.value })}
                rows={3}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder="Visas efter inskickat ärende"
              />
            </div>

            {/* Category and Type Selection */}
            <div className="border-t pt-6">
              <h3 className="font-medium text-gray-900 mb-4">Kategorisering</h3>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Tjänstetyp
                  </label>
                  <select
                    value={flowForm.typeId || ''}
                    onChange={(e) => {
                      const newTypeId = e.target.value || undefined;
                      setFlowForm({
                        ...flowForm,
                        typeId: newTypeId,
                        categoryId: undefined // Reset category when type changes
                      });
                    }}
                    className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 bg-white"
                  >
                    <option value="">Ingen tjänstetyp</option>
                    {flowTypes?.map((type) => (
                      <option key={type.id} value={type.id}>
                        {type.name}
                      </option>
                    ))}
                  </select>
                  <p className="text-xs text-gray-500 mt-1">Huvudkategori för e-tjänsten</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Kategori
                  </label>
                  <select
                    value={flowForm.categoryId || ''}
                    onChange={(e) => setFlowForm({ ...flowForm, categoryId: e.target.value || undefined })}
                    disabled={!flowForm.typeId}
                    className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 bg-white disabled:bg-gray-100 disabled:text-gray-500"
                  >
                    <option value="">Ingen kategori</option>
                    {flowTypes
                      ?.find((t) => t.id === flowForm.typeId)
                      ?.categories.map((cat) => (
                        <option key={cat.id} value={cat.id}>
                          {cat.name}
                        </option>
                      ))}
                  </select>
                  <p className="text-xs text-gray-500 mt-1">
                    {flowForm.typeId ? 'Underkategori inom tjänstetypen' : 'Välj tjänstetyp först'}
                  </p>
                </div>
              </div>
            </div>

            <div className="border-t pt-6">
              <h3 className="font-medium text-gray-900 mb-4">Inställningar</h3>
              <div className="space-y-4">
                <label className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={flowForm.requireAuth}
                    onChange={(e) => setFlowForm({ ...flowForm, requireAuth: e.target.checked })}
                    className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                  />
                  <div>
                    <span className="text-sm font-medium text-gray-700">Kräv inloggning</span>
                    <p className="text-xs text-gray-500">Användare måste logga in för att använda e-tjänsten</p>
                  </div>
                </label>

                <label className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={flowForm.requireSigning}
                    onChange={(e) => setFlowForm({ ...flowForm, requireSigning: e.target.checked })}
                    className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                  />
                  <div>
                    <span className="text-sm font-medium text-gray-700">Kräv signering</span>
                    <p className="text-xs text-gray-500">Användare måste signera med BankID/Freja</p>
                  </div>
                </label>

                <label className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={flowForm.allowSaveDraft}
                    onChange={(e) => setFlowForm({ ...flowForm, allowSaveDraft: e.target.checked })}
                    className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                  />
                  <div>
                    <span className="text-sm font-medium text-gray-700">Tillåt spara utkast</span>
                    <p className="text-xs text-gray-500">Användare kan spara och fortsätta senare</p>
                  </div>
                </label>

                <label className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={flowForm.allowMultiple}
                    onChange={(e) => setFlowForm({ ...flowForm, allowMultiple: e.target.checked })}
                    className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                  />
                  <div>
                    <span className="text-sm font-medium text-gray-700">Tillåt flera ärenden</span>
                    <p className="text-xs text-gray-500">Användare kan skicka in flera ärenden</p>
                  </div>
                </label>

                <label className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={flowForm.enabled}
                    onChange={(e) => setFlowForm({ ...flowForm, enabled: e.target.checked })}
                    className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                  />
                  <div>
                    <span className="text-sm font-medium text-gray-700">Aktiverad</span>
                    <p className="text-xs text-gray-500">E-tjänsten är synlig för användare</p>
                  </div>
                </label>
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'preview' && (
        <div className="bg-white rounded-lg border shadow-sm p-6">
          <p className="text-gray-500 text-center py-8">
            Förhandsgranskning kommer snart.
            <br />
            <Link href={`/citizen/services/${flowId}`} className="text-purple-600 hover:underline">
              Öppna i nytt fönster
            </Link>
          </p>
        </div>
      )}

      {/* Add Step Modal */}
      {showStepModal && (
        <StepModal
          step={editingStep}
          existingStepsCount={flow?.steps.length || 0}
          onSave={(stepData) => {
            if (editingStep) {
              updateStepMutation.mutate({ stepId: editingStep.id, step: stepData });
            } else {
              addStepMutation.mutate(stepData);
            }
          }}
          onClose={() => {
            setShowStepModal(false);
            setEditingStep(null);
          }}
          isLoading={addStepMutation.isPending || updateStepMutation.isPending}
        />
      )}

      {/* Add Query Modal */}
      {showAddQueryModal && (
        <AddQueryModal
          existingQueriesCount={selectedStep?.queries.length || 0}
          onAdd={(queryData) => addQueryMutation.mutate(queryData)}
          onClose={() => setShowAddQueryModal(false)}
          isLoading={addQueryMutation.isPending}
        />
      )}

      {/* Edit Query Modal */}
      {editingQuery && (
        <EditQueryModal
          query={editingQuery}
          onSave={(queryData) => updateQueryMutation.mutate({ queryId: editingQuery.id, query: queryData })}
          onClose={() => setEditingQuery(null)}
          isLoading={updateQueryMutation.isPending}
        />
      )}
    </div>
  );
}

// Query Card Component
function QueryCard({
  query,
  onEdit,
  onDelete,
}: {
  query: QueryDefinitionDetail;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const typeInfo = queryTypes.find((t) => t.value === query.queryType) || { label: query.queryType, icon: '?' };

  return (
    <div className="border rounded-lg p-4 hover:border-purple-300 transition-colors">
      <div className="flex items-start gap-3">
        <div className="w-8 h-8 bg-purple-100 rounded flex items-center justify-center text-purple-600 font-medium text-sm">
          {typeInfo.icon}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h4 className="font-medium text-gray-900 truncate">{query.name}</h4>
            {query.required && (
              <span className="text-xs bg-red-100 text-red-600 px-1.5 py-0.5 rounded">Obligatoriskt</span>
            )}
          </div>
          <p className="text-sm text-gray-500">{typeInfo.label}</p>
          {query.description && (
            <p className="text-sm text-gray-400 mt-1 truncate">{query.description}</p>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={onEdit}
            className="p-1 text-gray-400 hover:text-purple-600 rounded"
            title="Redigera"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </button>
          <button
            onClick={onDelete}
            className="p-1 text-gray-400 hover:text-red-600 rounded"
            title="Ta bort"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}

// Step Modal Component
function StepModal({
  step,
  existingStepsCount,
  onSave,
  onClose,
  isLoading,
}: {
  step: StepDetail | null;
  existingStepsCount: number;
  onSave: (step: StepInput) => void;
  onClose: () => void;
  isLoading: boolean;
}) {
  const [name, setName] = useState(step?.name || '');
  const [description, setDescription] = useState(step?.description || '');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave({
      name,
      description,
      sortOrder: step?.sortOrder ?? existingStepsCount,
      queries: step?.queries.map((q) => ({
        id: q.id,
        name: q.name,
        description: q.description,
        helpText: q.helpText,
        placeholder: q.placeholder,
        queryType: q.queryType,
        config: q.config,
        required: q.required,
        defaultState: q.defaultState,
        sortOrder: q.sortOrder,
        width: q.width as 'FULL' | 'HALF' | 'THIRD',
      })) || [],
    });
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6">
        <h3 className="text-lg font-bold text-gray-900 mb-4">
          {step ? 'Redigera steg' : 'Nytt steg'}
        </h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Namn *
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              placeholder="t.ex. Uppgifter om sökande"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Beskrivning
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              placeholder="Kort beskrivning av steget"
            />
          </div>
          <div className="flex justify-end gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border rounded-lg text-gray-700 hover:bg-gray-50"
            >
              Avbryt
            </button>
            <button
              type="submit"
              disabled={isLoading || !name.trim()}
              className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50"
            >
              {isLoading ? 'Sparar...' : step ? 'Spara' : 'Lägg till'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// Add Query Modal Component
function AddQueryModal({
  existingQueriesCount,
  onAdd,
  onClose,
  isLoading,
}: {
  existingQueriesCount: number;
  onAdd: (query: QueryDefinitionInput) => void;
  onClose: () => void;
  isLoading: boolean;
}) {
  const [name, setName] = useState('');
  const [queryType, setQueryType] = useState('TEXT');
  const [required, setRequired] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onAdd({
      name,
      queryType,
      required,
      config: {},
      sortOrder: existingQueriesCount,
    });
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4 p-6">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Lägg till fält</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Fältnamn *
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              placeholder="t.ex. Förnamn"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Fälttyp *
            </label>
            <div className="grid grid-cols-3 gap-2 max-h-60 overflow-y-auto">
              {queryTypes.map((type) => (
                <button
                  key={type.value}
                  type="button"
                  onClick={() => setQueryType(type.value)}
                  className={`p-3 border rounded-lg text-left transition-colors ${
                    queryType === type.value
                      ? 'border-purple-500 bg-purple-50 text-purple-700'
                      : 'hover:border-gray-400'
                  }`}
                >
                  <div className="text-lg mb-1">{type.icon}</div>
                  <div className="text-xs font-medium truncate">{type.label}</div>
                </button>
              ))}
            </div>
          </div>

          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={required}
              onChange={(e) => setRequired(e.target.checked)}
              className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
            />
            <span className="text-sm text-gray-700">Obligatoriskt fält</span>
          </label>

          <div className="flex justify-end gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border rounded-lg text-gray-700 hover:bg-gray-50"
            >
              Avbryt
            </button>
            <button
              type="submit"
              disabled={isLoading || !name.trim()}
              className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50"
            >
              {isLoading ? 'Lägger till...' : 'Lägg till'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// Edit Query Modal Component
function EditQueryModal({
  query,
  onSave,
  onClose,
  isLoading,
}: {
  query: QueryDefinitionDetail;
  onSave: (query: QueryDefinitionInput) => void;
  onClose: () => void;
  isLoading: boolean;
}) {
  const [name, setName] = useState(query.name);
  const [description, setDescription] = useState(query.description || '');
  const [helpText, setHelpText] = useState(query.helpText || '');
  const [placeholder, setPlaceholder] = useState(query.placeholder || '');
  const [required, setRequired] = useState(query.required);
  const [config, setConfig] = useState(query.config);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave({
      name,
      description,
      helpText,
      placeholder,
      queryType: query.queryType,
      config,
      required,
      defaultState: query.defaultState,
      sortOrder: query.sortOrder,
      width: query.width as 'FULL' | 'HALF' | 'THIRD',
    });
  };

  // Config editors for different query types
  const renderConfigEditor = () => {
    switch (query.queryType) {
      case 'SELECT':
      case 'RADIO':
      case 'CHECKBOX':
        return (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Alternativ (ett per rad)
            </label>
            <textarea
              value={(config.options as { value: string; label: string }[] || [])
                .map((o) => o.label)
                .join('\n')}
              onChange={(e) => {
                const options = e.target.value
                  .split('\n')
                  .filter((line) => line.trim())
                  .map((label, i) => ({
                    value: label.toLowerCase().replace(/\s+/g, '_'),
                    label: label.trim(),
                  }));
                setConfig({ ...config, options });
              }}
              rows={4}
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 font-mono text-sm"
              placeholder="Alternativ 1&#10;Alternativ 2&#10;Alternativ 3"
            />
          </div>
        );
      case 'TEXT':
      case 'TEXTAREA':
        return (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Min längd
              </label>
              <input
                type="number"
                value={(config.minLength as number) || ''}
                onChange={(e) => setConfig({ ...config, minLength: e.target.value ? parseInt(e.target.value) : undefined })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                min={0}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Max längd
              </label>
              <input
                type="number"
                value={(config.maxLength as number) || ''}
                onChange={(e) => setConfig({ ...config, maxLength: e.target.value ? parseInt(e.target.value) : undefined })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                min={0}
              />
            </div>
          </div>
        );
      case 'NUMBER':
        return (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Min värde
              </label>
              <input
                type="number"
                value={(config.min as number) || ''}
                onChange={(e) => setConfig({ ...config, min: e.target.value ? parseInt(e.target.value) : undefined })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Max värde
              </label>
              <input
                type="number"
                value={(config.max as number) || ''}
                onChange={(e) => setConfig({ ...config, max: e.target.value ? parseInt(e.target.value) : undefined })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
          </div>
        );
      case 'FILE':
        return (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Tillåtna filtyper
              </label>
              <input
                type="text"
                value={(config.accept as string) || ''}
                onChange={(e) => setConfig({ ...config, accept: e.target.value })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder=".pdf,.jpg,.png"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Max filstorlek (MB)
              </label>
              <input
                type="number"
                value={((config.maxSize as number) || 0) / (1024 * 1024) || ''}
                onChange={(e) => setConfig({ ...config, maxSize: e.target.value ? parseInt(e.target.value) * 1024 * 1024 : undefined })}
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                min={0}
              />
            </div>
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4 p-6 max-h-[90vh] overflow-y-auto">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Redigera fält</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Fältnamn *
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Beskrivning
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              placeholder="Visas under fältnamnet"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Hjälptext
            </label>
            <textarea
              value={helpText}
              onChange={(e) => setHelpText(e.target.value)}
              rows={2}
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              placeholder="Visas vid klick på hjälp-ikonen"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Placeholder
            </label>
            <input
              type="text"
              value={placeholder}
              onChange={(e) => setPlaceholder(e.target.value)}
              className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
              placeholder="Exempeltext i fältet"
            />
          </div>

          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={required}
              onChange={(e) => setRequired(e.target.checked)}
              className="w-4 h-4 rounded border-gray-300 text-purple-600 focus:ring-purple-500"
            />
            <span className="text-sm text-gray-700">Obligatoriskt fält</span>
          </label>

          {/* Type-specific config */}
          {renderConfigEditor()}

          <div className="flex justify-end gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border rounded-lg text-gray-700 hover:bg-gray-50"
            >
              Avbryt
            </button>
            <button
              type="submit"
              disabled={isLoading || !name.trim()}
              className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50"
            >
              {isLoading ? 'Sparar...' : 'Spara'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
