/**
 * Form field types matching backend QueryType enum.
 */
export type QueryType =
  // Text inputs
  | 'TEXT'
  | 'TEXTAREA'
  | 'NUMBER'
  | 'EMAIL'
  | 'PHONE'
  | 'URL'
  // Date/Time
  | 'DATE'
  | 'DATETIME'
  | 'TIME'
  // Selection
  | 'SELECT'
  | 'MULTISELECT'
  | 'RADIO'
  | 'CHECKBOX'
  // Files
  | 'FILE'
  | 'IMAGE'
  // Special
  | 'MAP'
  | 'LOCATION'
  | 'SIGNATURE'
  | 'ORGANIZATION'
  | 'PERSON'
  // Layout elements
  | 'HEADING'
  | 'PARAGRAPH'
  | 'DIVIDER';

export type QueryState = 'VISIBLE' | 'VISIBLE_REQUIRED' | 'HIDDEN';

export type EvaluatorType =
  | 'VALUE_EQUALS'
  | 'VALUE_NOT_EQUALS'
  | 'VALUE_IN'
  | 'VALUE_NOT_IN'
  | 'VALUE_CONTAINS'
  | 'VALUE_NOT_CONTAINS'
  | 'VALUE_GREATER_THAN'
  | 'VALUE_LESS_THAN'
  | 'VALUE_BETWEEN'
  | 'REGEX_MATCH'
  | 'IS_EMPTY'
  | 'IS_NOT_EMPTY'
  | 'CUSTOM';

export interface SelectOption {
  value: string;
  label: string;
}

export interface QueryConfig {
  // Text fields
  maxLength?: number;
  minLength?: number;
  pattern?: string;
  // Number fields
  min?: number;
  max?: number;
  step?: number;
  // Select/Radio/Checkbox
  options?: SelectOption[];
  // File fields
  accept?: string;
  maxSize?: number;
  multiple?: boolean;
  // Layout
  level?: 1 | 2 | 3 | 4;
  content?: string;
}

export interface Evaluator {
  id: string;
  evaluatorType: EvaluatorType;
  condition: Record<string, unknown>;
  targetQueryIds: string[];
  targetState: QueryState;
}

export interface QueryDefinition {
  id: string;
  name: string;
  description?: string;
  helpText?: string;
  placeholder?: string;
  queryType: QueryType;
  config: QueryConfig;
  required: boolean;
  defaultState: QueryState;
  sortOrder: number;
  width: 'FULL' | 'HALF' | 'THIRD';
  evaluators: Evaluator[];
}

export interface Step {
  id: string;
  name: string;
  description?: string;
  sortOrder: number;
  queries: QueryDefinition[];
}

export interface Flow {
  id: string;
  name: string;
  version: number;
  shortDescription?: string;
  longDescription?: string;
  status: string;
  enabled: boolean;
  requireAuth: boolean;
  requireSigning: boolean;
  tags?: string[];
  typeId?: string;
  typeName?: string;
  categoryId?: string;
  categoryName?: string;
  familyId?: string;
  steps: Step[];
  createdAt: string;
  updatedAt: string;
}

export type FormValues = Record<string, unknown>;

export interface FieldState {
  state: QueryState;
  errors: string[];
  touched: boolean;
}

export type FieldStates = Record<string, FieldState>;

export interface FormContext {
  values: FormValues;
  fieldStates: FieldStates;
  setValue: (queryId: string, value: unknown) => void;
  setTouched: (queryId: string) => void;
  getFieldState: (queryId: string) => FieldState;
  userId?: string;
  caseId?: string;
}
