import { api } from './client';

/**
 * Admin API for managing flows and e-services.
 */

export interface FlowSummary {
  id: string;
  name: string;
  version: number;
  shortDescription?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  enabled: boolean;
  requireAuth: boolean;
  requireSigning: boolean;
  tags?: string[];
  typeId?: string;
  typeName?: string;
  categoryId?: string;
  categoryName?: string;
  familyId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface StepInput {
  id?: string;
  name: string;
  description?: string;
  sortOrder: number;
  queries: QueryDefinitionInput[];
}

export interface QueryDefinitionInput {
  id?: string;
  name: string;
  description?: string;
  helpText?: string;
  placeholder?: string;
  queryType: string;
  config: Record<string, unknown>;
  required: boolean;
  defaultState?: 'VISIBLE' | 'HIDDEN' | 'DISABLED';
  sortOrder: number;
  width?: 'FULL' | 'HALF' | 'THIRD';
}

export interface FlowDetail extends FlowSummary {
  longDescription?: string;
  submittedMessage?: string;
  sequentialSigning: boolean;
  allowSaveDraft: boolean;
  allowMultiple: boolean;
  publishDate?: string;
  unpublishDate?: string;
  externalLink?: string;
  steps: StepDetail[];
}

export interface StepDetail {
  id: string;
  name: string;
  description?: string;
  sortOrder: number;
  queries: QueryDefinitionDetail[];
}

export interface QueryDefinitionDetail {
  id: string;
  name: string;
  description?: string;
  helpText?: string;
  placeholder?: string;
  queryType: string;
  config: Record<string, unknown>;
  required: boolean;
  defaultState: 'VISIBLE' | 'HIDDEN' | 'DISABLED';
  sortOrder: number;
  width: string;
  evaluators?: EvaluatorDetail[];
}

export interface EvaluatorDetail {
  id: string;
  evaluatorType: string;
  config: Record<string, unknown>;
  action: string;
  sortOrder: number;
}

export interface CreateFlowRequest {
  name: string;
  shortDescription?: string;
  longDescription?: string;
  typeId?: string;
  categoryId?: string;
  requireAuth?: boolean;
  requireSigning?: boolean;
  tags?: string[];
}

export interface UpdateFlowRequest extends CreateFlowRequest {
  submittedMessage?: string;
  sequentialSigning?: boolean;
  allowSaveDraft?: boolean;
  allowMultiple?: boolean;
  externalLink?: string;
  enabled?: boolean;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface Category {
  id: string;
  name: string;
  description?: string;
  sortOrder: number;
  flowTypeId?: string;
  flowTypeName?: string;
}

export interface FlowType {
  id: string;
  name: string;
  description?: string;
  color?: string;
  icon?: string;
  sortOrder: number;
  categories: Category[];
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
  sortOrder?: number;
  flowTypeId?: string;
}

export interface CreateFlowTypeRequest {
  name: string;
  description?: string;
  color?: string;
  icon?: string;
  sortOrder?: number;
}

// Flow API

/**
 * Get all flows (including drafts) for admin view.
 */
export async function getAllFlows(
  page = 0,
  size = 20,
  sort = 'updatedAt,desc'
): Promise<PaginatedResponse<FlowSummary>> {
  return api.get<PaginatedResponse<FlowSummary>>(
    `/api/v1/admin/flows?page=${page}&size=${size}&sort=${sort}`
  );
}

/**
 * Get a flow with all details for editing.
 */
export async function getFlowForEdit(flowId: string): Promise<FlowDetail> {
  return api.get<FlowDetail>(`/api/v1/admin/flows/${flowId}`);
}

/**
 * Create a new flow.
 */
export async function createFlow(request: CreateFlowRequest): Promise<FlowDetail> {
  return api.post<FlowDetail>('/api/v1/admin/flows', request);
}

/**
 * Update flow metadata.
 */
export async function updateFlow(flowId: string, request: UpdateFlowRequest): Promise<FlowDetail> {
  return api.put<FlowDetail>(`/api/v1/admin/flows/${flowId}`, request);
}

/**
 * Delete a flow (only drafts).
 */
export async function deleteFlow(flowId: string): Promise<void> {
  return api.delete(`/api/v1/admin/flows/${flowId}`);
}

/**
 * Publish a flow.
 */
export async function publishFlow(flowId: string): Promise<FlowDetail> {
  return api.post<FlowDetail>(`/api/v1/admin/flows/${flowId}/publish`);
}

/**
 * Archive a flow.
 */
export async function archiveFlow(flowId: string): Promise<FlowDetail> {
  return api.post<FlowDetail>(`/api/v1/admin/flows/${flowId}/archive`);
}

/**
 * Duplicate a flow (create new version or copy).
 */
export async function duplicateFlow(flowId: string): Promise<FlowDetail> {
  return api.post<FlowDetail>(`/api/v1/admin/flows/${flowId}/duplicate`);
}

// Step API

/**
 * Add a step to a flow.
 */
export async function addStep(flowId: string, step: StepInput): Promise<StepDetail> {
  return api.post<StepDetail>(`/api/v1/admin/flows/${flowId}/steps`, step);
}

/**
 * Update a step.
 */
export async function updateStep(flowId: string, stepId: string, step: StepInput): Promise<StepDetail> {
  return api.put<StepDetail>(`/api/v1/admin/flows/${flowId}/steps/${stepId}`, step);
}

/**
 * Delete a step.
 */
export async function deleteStep(flowId: string, stepId: string): Promise<void> {
  return api.delete(`/api/v1/admin/flows/${flowId}/steps/${stepId}`);
}

/**
 * Reorder steps.
 */
export async function reorderSteps(flowId: string, stepIds: string[]): Promise<void> {
  return api.put(`/api/v1/admin/flows/${flowId}/steps/reorder`, { stepIds });
}

// Query Definition API

/**
 * Add a query definition to a step.
 */
export async function addQueryDefinition(
  flowId: string,
  stepId: string,
  query: QueryDefinitionInput
): Promise<QueryDefinitionDetail> {
  return api.post<QueryDefinitionDetail>(
    `/api/v1/admin/flows/${flowId}/steps/${stepId}/queries`,
    query
  );
}

/**
 * Update a query definition.
 */
export async function updateQueryDefinition(
  flowId: string,
  stepId: string,
  queryId: string,
  query: QueryDefinitionInput
): Promise<QueryDefinitionDetail> {
  return api.put<QueryDefinitionDetail>(
    `/api/v1/admin/flows/${flowId}/steps/${stepId}/queries/${queryId}`,
    query
  );
}

/**
 * Delete a query definition.
 */
export async function deleteQueryDefinition(
  flowId: string,
  stepId: string,
  queryId: string
): Promise<void> {
  return api.delete(`/api/v1/admin/flows/${flowId}/steps/${stepId}/queries/${queryId}`);
}

/**
 * Reorder queries within a step.
 */
export async function reorderQueries(
  flowId: string,
  stepId: string,
  queryIds: string[]
): Promise<void> {
  return api.put(`/api/v1/admin/flows/${flowId}/steps/${stepId}/queries/reorder`, { queryIds });
}

// Categories API

/**
 * Get all categories.
 */
export async function getCategories(): Promise<Category[]> {
  return api.get<Category[]>('/api/v1/admin/categories');
}

/**
 * Get a single category.
 */
export async function getCategory(id: string): Promise<Category> {
  return api.get<Category>(`/api/v1/admin/categories/${id}`);
}

/**
 * Create a new category.
 */
export async function createCategory(request: CreateCategoryRequest): Promise<Category> {
  return api.post<Category>('/api/v1/admin/categories', request);
}

/**
 * Update a category.
 */
export async function updateCategory(id: string, request: CreateCategoryRequest): Promise<Category> {
  return api.put<Category>(`/api/v1/admin/categories/${id}`, request);
}

/**
 * Delete a category.
 */
export async function deleteCategory(id: string): Promise<void> {
  return api.delete(`/api/v1/admin/categories/${id}`);
}

// Flow Types API

/**
 * Get all flow types.
 */
export async function getFlowTypes(): Promise<FlowType[]> {
  return api.get<FlowType[]>('/api/v1/admin/flow-types');
}

/**
 * Get a single flow type.
 */
export async function getFlowType(id: string): Promise<FlowType> {
  return api.get<FlowType>(`/api/v1/admin/flow-types/${id}`);
}

/**
 * Create a new flow type.
 */
export async function createFlowType(request: CreateFlowTypeRequest): Promise<FlowType> {
  return api.post<FlowType>('/api/v1/admin/flow-types', request);
}

/**
 * Update a flow type.
 */
export async function updateFlowType(id: string, request: CreateFlowTypeRequest): Promise<FlowType> {
  return api.put<FlowType>(`/api/v1/admin/flow-types/${id}`, request);
}

/**
 * Delete a flow type.
 */
export async function deleteFlowType(id: string): Promise<void> {
  return api.delete(`/api/v1/admin/flow-types/${id}`);
}
