import type { Flow, QueryDefinition } from '@/components/form';
import type { QueryInstance } from '@/lib/api/cases';

export interface DisplayValue {
  id: string;
  label: string;
  value: string;
  stepName?: string;
}

const LAYOUT_TYPES = ['HEADING', 'PARAGRAPH', 'DIVIDER'];

function optionLabel(query: QueryDefinition | undefined, raw: unknown): string {
  const option = query?.config?.options?.find((o) => o.value === raw);
  return option ? option.label : String(raw);
}

function joinParts(record: Record<string, unknown>, keys: string[]): string {
  return keys
    .map((k) => record[k])
    .filter((v) => v !== undefined && v !== null && v !== '')
    .join(', ');
}

/** Formats a stored answer for display, using the field definition when it is known. */
export function formatAnswer(value: unknown, query?: QueryDefinition): string {
  if (value === null || value === undefined || value === '') return '—';
  const type = query?.queryType;

  if (Array.isArray(value)) {
    if (value.length === 0) return '—';
    // Uploaded files are stored as attachment objects
    if (typeof value[0] === 'object' && value[0] !== null && 'originalFilename' in value[0]) {
      return value.map((f: { originalFilename: string }) => f.originalFilename).join(', ');
    }
    return value.map((v) => optionLabel(query, v)).join(', ');
  }

  if (typeof value === 'boolean') return value ? 'Ja' : 'Nej';

  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    switch (type) {
      case 'PERSON': {
        const name = [record.firstName, record.lastName].filter(Boolean).join(' ');
        return joinParts({ ...record, name }, ['name', 'personalNumber', 'email', 'phone']) || '—';
      }
      case 'ORGANIZATION':
        return joinParts(record, ['name', 'organizationNumber', 'contactPerson', 'email', 'phone', 'address', 'postalCode', 'city']) || '—';
      case 'LOCATION':
        return joinParts(record, ['address', 'postalCode', 'city', 'municipality']) || '—';
      case 'MAP':
        return joinParts(record, ['address', 'lat', 'lng']) || '—';
      default:
        return Object.values(record).filter((v) => v !== null && v !== '').map(String).join(', ') || '—';
    }
  }

  if (type === 'SELECT' || type === 'RADIO') return optionLabel(query, value);
  if (type === 'SIGNATURE' && typeof value === 'string' && value.startsWith('data:')) return 'Signerad';
  if (type === 'DATE' && typeof value === 'string') {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) return date.toLocaleDateString('sv-SE');
  }

  return String(value);
}

/**
 * Returns the populated, visible answers of a case in the order they appear in
 * the form. Answers whose field is missing from the flow are placed last.
 */
export function getDisplayValues(values: QueryInstance[], flow?: Flow): DisplayValue[] {
  const order = new Map<string, { index: number; query: QueryDefinition; stepName: string }>();
  let index = 0;
  [...(flow?.steps ?? [])]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .forEach((step) => {
      [...step.queries]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .forEach((query) => order.set(query.id, { index: index++, query, stepName: step.name }));
    });

  return values
    .filter((qi) => qi.state !== 'HIDDEN' && qi.populated)
    .filter((qi) => !LAYOUT_TYPES.includes(order.get(qi.queryDefinitionId)?.query.queryType ?? ''))
    .sort(
      (a, b) =>
        (order.get(a.queryDefinitionId)?.index ?? Number.MAX_SAFE_INTEGER) -
        (order.get(b.queryDefinitionId)?.index ?? Number.MAX_SAFE_INTEGER)
    )
    .map((qi) => {
      const meta = order.get(qi.queryDefinitionId);
      return {
        id: qi.id,
        label: qi.queryName,
        value: formatAnswer(qi.value, meta?.query),
        stepName: meta?.stepName,
      };
    });
}
