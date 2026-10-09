'use client';

import { useId, useState } from 'react';
import type {
  EvaluatorDetail,
  EvaluatorInput,
  EvaluatorType,
  QueryDefinitionDetail,
  StepDetail,
  TargetState,
} from '@/lib/api/admin';

/** Field types whose answers can't drive a condition in a meaningful way. */
const NON_SOURCE_TYPES = ['HEADING', 'PARAGRAPH', 'DIVIDER', 'SIGNATURE', 'MAP', 'PERSON', 'ORGANIZATION', 'LOCATION'];
const LAYOUT_TYPES = ['HEADING', 'PARAGRAPH', 'DIVIDER'];

interface Operator {
  type: EvaluatorType;
  label: string;
  /** What kind of value the operator needs. */
  value: 'none' | 'single' | 'multiple' | 'number';
}

const OPERATORS: Operator[] = [
  { type: 'VALUE_EQUALS', label: 'är', value: 'single' },
  { type: 'VALUE_NOT_EQUALS', label: 'är inte', value: 'single' },
  { type: 'VALUE_IN', label: 'är något av', value: 'multiple' },
  { type: 'VALUE_CONTAINS', label: 'innehåller', value: 'single' },
  { type: 'VALUE_GREATER_THAN', label: 'är större än', value: 'number' },
  { type: 'VALUE_LESS_THAN', label: 'är mindre än', value: 'number' },
  { type: 'IS_NOT_EMPTY', label: 'är ifyllt', value: 'none' },
  { type: 'IS_EMPTY', label: 'är tomt', value: 'none' },
];

/** label: short form for summaries; sentence: fits "så ska följande fält …". */
const ACTIONS: { state: TargetState; label: string; sentence: string }[] = [
  { state: 'VISIBLE', label: 'visa', sentence: 'visas' },
  { state: 'VISIBLE_REQUIRED', label: 'visa och kräv', sentence: 'visas och vara obligatoriska' },
  { state: 'HIDDEN', label: 'dölj', sentence: 'döljas' },
];

type Option = { value: string; label: string };

function optionsOf(query: QueryDefinitionDetail): Option[] | null {
  if (query.queryType === 'CHECKBOX') {
    return [
      { value: 'true', label: 'Ikryssad' },
      { value: 'false', label: 'Inte ikryssad' },
    ];
  }
  const options = query.config?.options;
  return Array.isArray(options) ? (options as Option[]) : null;
}

function operatorsFor(query: QueryDefinitionDetail): Operator[] {
  if (query.queryType === 'CHECKBOX') {
    return OPERATORS.filter((o) => o.type === 'VALUE_EQUALS');
  }
  if (optionsOf(query)) {
    return OPERATORS.filter((o) => ['VALUE_EQUALS', 'VALUE_NOT_EQUALS', 'VALUE_IN', 'IS_NOT_EMPTY', 'IS_EMPTY'].includes(o.type));
  }
  if (['NUMBER', 'CURRENCY'].includes(query.queryType)) {
    return OPERATORS.filter((o) => o.type !== 'VALUE_IN' && o.type !== 'VALUE_CONTAINS');
  }
  if (['FILE', 'IMAGE', 'MULTISELECT'].includes(query.queryType)) {
    return OPERATORS.filter((o) => o.type === 'IS_NOT_EMPTY' || o.type === 'IS_EMPTY');
  }
  return OPERATORS.filter((o) => o.value !== 'number' && o.type !== 'VALUE_IN');
}

/** Plain-language summary, e.g. "är Företag/Organisation → visa och kräv Organisationsnamn". */
export function describeCondition(
  evaluator: EvaluatorDetail,
  source: QueryDefinitionDetail,
  allQueries: QueryDefinitionDetail[]
): string {
  const operator = OPERATORS.find((o) => o.type === evaluator.evaluatorType);
  const options = optionsOf(source);
  const label = (v: unknown) => options?.find((o) => o.value === String(v))?.label ?? String(v);

  let value = '';
  if (operator?.value === 'single' || operator?.value === 'number') value = ` ${label(evaluator.condition.value)}`;
  if (operator?.value === 'multiple') {
    value = ` ${((evaluator.condition.values as unknown[]) ?? []).map(label).join(', ')}`;
  }
  const action = ACTIONS.find((a) => a.state === evaluator.targetState)?.label ?? evaluator.targetState;
  const targets = evaluator.targetQueryIds
    .map((id) => allQueries.find((q) => q.id === id)?.name ?? 'okänt fält')
    .join(', ');
  return `${operator?.label ?? evaluator.evaluatorType}${value} → ${action} ${targets}`;
}

export function canHaveConditions(query: QueryDefinitionDetail) {
  return !NON_SOURCE_TYPES.includes(query.queryType);
}

interface ConditionEditorProps {
  source: QueryDefinitionDetail;
  steps: StepDetail[];
  onAdd: (evaluator: EvaluatorInput) => Promise<void>;
  onDelete: (evaluatorId: string) => Promise<void>;
  onClose: () => void;
}

/**
 * Modal for the conditions of one field ("when this field's answer is X,
 * show / require / hide these fields").
 */
export function ConditionEditor({ source, steps, onAdd, onDelete, onClose }: ConditionEditorProps) {
  const headingId = useId();
  const allQueries = steps.flatMap((s) => s.queries);
  const operators = operatorsFor(source);
  const options = optionsOf(source);

  const [operatorType, setOperatorType] = useState<EvaluatorType>(operators[0].type);
  const [value, setValue] = useState('');
  const [values, setValues] = useState<string[]>([]);
  const [action, setAction] = useState<TargetState>('VISIBLE');
  const [targets, setTargets] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const operator = operators.find((o) => o.type === operatorType) ?? operators[0];

  const buildCondition = (): Record<string, unknown> | null => {
    switch (operator.value) {
      case 'none':
        return {};
      case 'multiple':
        return values.length ? { values } : null;
      case 'number': {
        const n = Number(value.replace(',', '.'));
        return value.trim() && !Number.isNaN(n) ? { value: n } : null;
      }
      default:
        if (!value.trim()) return null;
        if (source.queryType === 'CHECKBOX') return { value: value === 'true' };
        return { value };
    }
  };

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    const condition = buildCondition();
    if (!condition) {
      setError('Ange ett värde för villkoret.');
      return;
    }
    if (targets.length === 0) {
      setError('Välj minst ett fält som villkoret ska påverka.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onAdd({ evaluatorType: operator.type, condition, targetQueryIds: targets, targetState: action });
      setValue('');
      setValues([]);
      setTargets([]);
    } catch (err) {
      const message = err && typeof err === 'object' && 'message' in err ? String(err.message) : '';
      setError(message || 'Villkoret kunde inte sparas.');
    } finally {
      setSaving(false);
    }
  };

  const toggle = (list: string[], item: string) =>
    list.includes(item) ? list.filter((x) => x !== item) : [...list, item];

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        className="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col"
      >
        <div className="px-6 py-4 border-b">
          <h2 id={headingId} className="text-lg font-semibold text-gray-900">
            Villkor för &quot;{source.name}&quot;
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            Styr vilka fält som visas eller krävs beroende på svaret i det här fältet.
          </p>
        </div>

        <div className="p-6 space-y-6 overflow-y-auto">
          {/* Existing conditions */}
          <section>
            <h3 className="text-sm font-medium text-gray-700 mb-2">Befintliga villkor</h3>
            {(source.evaluators ?? []).length === 0 ? (
              <p className="text-sm text-gray-500 italic">Inga villkor ännu.</p>
            ) : (
              <ul className="space-y-2">
                {source.evaluators!.map((ev) => (
                  <li key={ev.id} className="flex items-start justify-between gap-3 p-3 bg-purple-50 rounded-lg">
                    <p className="text-sm text-gray-800">
                      <span className="text-gray-500">När svaret </span>
                      {describeCondition(ev, source, allQueries)}
                    </p>
                    <button
                      type="button"
                      onClick={() => onDelete(ev.id).catch(() => setError('Villkoret kunde inte tas bort.'))}
                      className="text-sm text-red-600 hover:text-red-800 shrink-0"
                    >
                      Ta bort
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* New condition */}
          <form onSubmit={handleAdd} className="space-y-4 border-t pt-6">
            <h3 className="text-sm font-medium text-gray-700">Nytt villkor</h3>

            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-gray-700">När svaret</span>
              <label className="sr-only" htmlFor="condition-operator">Villkor</label>
              <select
                id="condition-operator"
                value={operatorType}
                onChange={(e) => {
                  setOperatorType(e.target.value as EvaluatorType);
                  setValue('');
                  setValues([]);
                }}
                className="px-2 py-1.5 border rounded-lg"
              >
                {operators.map((o) => (
                  <option key={o.type} value={o.type}>
                    {o.label}
                  </option>
                ))}
              </select>

              {operator.value === 'single' && options && (
                <>
                  <label className="sr-only" htmlFor="condition-value">Värde</label>
                  <select
                    id="condition-value"
                    value={value}
                    onChange={(e) => setValue(e.target.value)}
                    className="px-2 py-1.5 border rounded-lg"
                  >
                    <option value="">Välj…</option>
                    {options.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </>
              )}
              {(operator.value === 'number' || (operator.value === 'single' && !options)) && (
                <>
                  <label className="sr-only" htmlFor="condition-value">Värde</label>
                  <input
                    id="condition-value"
                    type={operator.value === 'number' ? 'number' : 'text'}
                    value={value}
                    onChange={(e) => setValue(e.target.value)}
                    className="px-2 py-1.5 border rounded-lg w-48"
                  />
                </>
              )}
            </div>

            {operator.value === 'multiple' && options && (
              <fieldset>
                <legend className="text-sm text-gray-700 mb-1">Något av:</legend>
                <div className="flex flex-wrap gap-3">
                  {options.map((o) => (
                    <label key={o.value} className="flex items-center gap-1.5 text-sm">
                      <input
                        type="checkbox"
                        checked={values.includes(o.value)}
                        onChange={() => setValues((v) => toggle(v, o.value))}
                      />
                      {o.label}
                    </label>
                  ))}
                </div>
              </fieldset>
            )}

            <div className="flex items-center gap-2 text-sm">
              <span className="text-gray-700">så ska följande fält</span>
              <label className="sr-only" htmlFor="condition-action">Åtgärd</label>
              <select
                id="condition-action"
                value={action}
                onChange={(e) => setAction(e.target.value as TargetState)}
                className="px-2 py-1.5 border rounded-lg"
              >
                {ACTIONS.map((a) => (
                  <option key={a.state} value={a.state}>
                    {a.sentence}
                  </option>
                ))}
              </select>
            </div>

            <fieldset className="border rounded-lg p-3 max-h-56 overflow-y-auto">
              <legend className="text-sm font-medium text-gray-700 px-1">Fält</legend>
              {steps.map((step) => {
                const candidates = step.queries.filter((q) => q.id !== source.id && !LAYOUT_TYPES.includes(q.queryType));
                if (candidates.length === 0) return null;
                return (
                  <div key={step.id} className="mb-2 last:mb-0">
                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{step.name}</p>
                    {candidates.map((q) => (
                      <label key={q.id} className="flex items-center gap-2 text-sm py-0.5">
                        <input
                          type="checkbox"
                          checked={targets.includes(q.id)}
                          onChange={() => setTargets((t) => toggle(t, q.id))}
                        />
                        {q.name}
                      </label>
                    ))}
                  </div>
                );
              })}
            </fieldset>

            {action !== 'HIDDEN' && (
              <p className="text-xs text-gray-500">
                Fält som visas av ett villkor är dolda tills villkoret uppfylls.
              </p>
            )}

            {error && (
              <p className="text-sm text-red-600" role="alert">
                {error}
              </p>
            )}

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={saving}
                className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50"
              >
                {saving ? 'Sparar…' : 'Lägg till villkor'}
              </button>
            </div>
          </form>
        </div>

        <div className="px-6 py-4 border-t bg-gray-50 flex justify-end rounded-b-lg">
          <button type="button" onClick={onClose} className="px-4 py-2 text-gray-700 hover:text-gray-900">
            Stäng
          </button>
        </div>
      </div>
    </div>
  );
}
