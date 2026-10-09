import { api } from './client';

/**
 * Case-related API calls.
 */

export interface CaseSummary {
  id: string;
  referenceNumber: string;
  flowId: string;
  flowName: string;
  statusId?: string;
  statusName?: string;
  statusColor?: string;
  currentStepIndex: number;
  totalSteps: number;
  isDraft: boolean;
  isCompleted: boolean;
  createdAt: string;
  updatedAt: string;
  submittedAt?: string;
}

export interface QueryInstance {
  id: string;
  queryDefinitionId: string;
  queryName: string;
  state: 'VISIBLE' | 'HIDDEN' | 'DISABLED' | 'READONLY';
  value: unknown;
  populated: boolean;
  validated: boolean;
}

export interface CaseDetail extends CaseSummary {
  values: QueryInstance[];
  categoryName?: string;
  typeName?: string;
}

export interface UpdateValuesRequest {
  [queryId: string]: unknown;
}

/**
 * Create a new case (draft) owned by the logged-in user.
 */
export async function createCase(flowId: string): Promise<CaseDetail> {
  return api.post<CaseDetail>('/api/v1/cases', { flowId });
}

/**
 * Get a case by ID.
 */
export async function getCase(caseId: string): Promise<CaseDetail> {
  return api.get<CaseDetail>(`/api/v1/cases/${caseId}`);
}

/**
 * Get a case by reference number.
 */
export async function getCaseByReference(referenceNumber: string): Promise<CaseDetail> {
  return api.get<CaseDetail>(`/api/v1/cases/ref/${referenceNumber}`);
}

/**
 * Get all cases for a user.
 */
export async function getCasesForUser(userId: string): Promise<{ content: CaseSummary[] }> {
  return api.get<{ content: CaseSummary[] }>(`/api/v1/cases/user/${userId}`);
}

/**
 * Get draft cases for a user.
 */
export async function getDraftsForUser(userId: string): Promise<CaseSummary[]> {
  return api.get<CaseSummary[]>(`/api/v1/cases/user/${userId}/drafts`);
}

/**
 * Update case values.
 */
export async function updateCaseValues(caseId: string, values: UpdateValuesRequest): Promise<CaseDetail> {
  return api.put<CaseDetail>(`/api/v1/cases/${caseId}/values`, values);
}

/**
 * Submit a case.
 */
export async function submitCase(caseId: string): Promise<CaseDetail> {
  return api.post<CaseDetail>(`/api/v1/cases/${caseId}/submit`);
}

/**
 * Delete a draft case.
 */
export async function deleteCase(caseId: string): Promise<void> {
  return api.delete<void>(`/api/v1/cases/${caseId}`);
}

export interface CaseEvent {
  id: string;
  eventType: string;
  description: string;
  createdAt: string;
  actorName: string | null;
  actorRole: 'ADMIN' | 'MANAGER' | 'USER' | null;
  oldStatusName: string | null;
  newStatusName: string | null;
  comment: string | null;
}

/**
 * Get the event history for a case (timeline).
 */
export async function getCaseEvents(caseId: string): Promise<CaseEvent[]> {
  return api.get<CaseEvent[]>(`/api/v1/cases/${caseId}/events`);
}

/**
 * Download the current user's case as PDF.
 */
export async function downloadOwnCasePdf(caseId: string, referenceNumber: string): Promise<void> {
  return api.downloadBlob(
    `/api/v1/cases/${caseId}/pdf/own`,
    `arende-${referenceNumber}.pdf`
  );
}
