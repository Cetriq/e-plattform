// Flow types
export interface Flow {
  id: string;
  name: string;
  version: number;
  shortDescription: string | null;
  longDescription: string | null;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  enabled: boolean;
  requireAuth: boolean;
  requireSigning: boolean;
  tags: string[];
  typeId: string | null;
  typeName: string | null;
  categoryId: string | null;
  categoryName: string | null;
  familyId: string | null;
  steps: Step[];
  createdAt: string;
  updatedAt: string;
}

export interface Step {
  id: string;
  name: string;
  description: string | null;
  sortOrder: number;
  queries: QueryDefinition[];
}

export interface QueryDefinition {
  id: string;
  name: string;
  description: string | null;
  helpText: string | null;
  placeholder: string | null;
  queryType: QueryType;
  config: Record<string, unknown>;
  required: boolean;
  defaultState: QueryState;
  sortOrder: number;
  width: string;
  evaluators: Evaluator[];
}

export type QueryType =
  | 'TEXT'
  | 'TEXTAREA'
  | 'NUMBER'
  | 'EMAIL'
  | 'PHONE'
  | 'URL'
  | 'DATE'
  | 'DATETIME'
  | 'TIME'
  | 'SELECT'
  | 'MULTISELECT'
  | 'RADIO'
  | 'CHECKBOX'
  | 'FILE'
  | 'IMAGE'
  | 'MAP'
  | 'LOCATION'
  | 'SIGNATURE'
  | 'ORGANIZATION'
  | 'PERSON'
  | 'HEADING'
  | 'PARAGRAPH'
  | 'DIVIDER';

export type QueryState = 'VISIBLE' | 'VISIBLE_REQUIRED' | 'HIDDEN';

export interface Evaluator {
  id: string;
  evaluatorType: string;
  condition: Record<string, unknown>;
  targetQueryIds: string[];
  targetState: QueryState;
}

// Case types
export interface Case {
  id: string;
  referenceNumber: string;
  flowId: string;
  flowName: string;
  statusId: string | null;
  statusName: string | null;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  currentStepIndex: number;
  userDescription: string | null;
  isDraft: boolean;
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
  completedAt: string | null;
  values: QueryInstance[] | null;
}

export interface QueryInstance {
  id: string;
  queryDefinitionId: string;
  queryName: string;
  state: QueryState;
  value: unknown;
  populated: boolean;
  validated: boolean;
}

// Pagination
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}
