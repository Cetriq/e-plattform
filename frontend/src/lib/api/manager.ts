import { api } from './client';
import type { CaseSummary, CaseDetail } from './cases';

/**
 * Manager-specific API calls.
 */

export interface StatusDefinition {
  id: string;
  name: string;
  description?: string;
  statusType: string;
  color?: string;
  icon?: string;
  userCanEdit: boolean;
  userCanDelete: boolean;
  userCanMessage: boolean;
  managerCanEdit: boolean;
  handlingDays?: number;
  sortOrder: number;
}

export interface ChangeStatusRequest {
  statusId: string;
  userId: string;
  comment?: string;
}

export interface CaseEvent {
  id: string;
  eventType: string;
  description?: string;
  createdAt: string;
  userId?: string;
  userName?: string;
  metadata?: Record<string, unknown>;
}

export interface InternalMessage {
  id: string;
  message: string;
  createdAt: string;
  userId: string;
  userName: string;
}

export interface ExternalMessage {
  id: string;
  message: string;
  fromManager: boolean;
  createdAt: string;
  userId: string;
  userName: string;
  readAt?: string;
}

export interface ManagerCaseDetail extends CaseDetail {
  events: CaseEvent[];
  internalMessages: InternalMessage[];
  externalMessages: ExternalMessage[];
  statusDefinitions: StatusDefinition[];
  owners: { id: string; name: string }[];
  createdByName: string;
  managerDescription?: string;
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

/**
 * Get all submitted cases (for managers).
 */
export async function getSubmittedCases(
  page = 0,
  size = 20,
  sort = 'submittedAt,desc'
): Promise<PaginatedResponse<CaseSummary>> {
  return api.get<PaginatedResponse<CaseSummary>>(
    `/api/v1/cases?page=${page}&size=${size}&sort=${sort}`
  );
}

/**
 * Search cases.
 */
export async function searchCases(
  query: string,
  page = 0,
  size = 20
): Promise<PaginatedResponse<CaseSummary>> {
  return api.get<PaginatedResponse<CaseSummary>>(
    `/api/v1/cases/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`
  );
}

/**
 * Get case detail for manager view (includes events, messages, etc.).
 */
export async function getManagerCaseDetail(caseId: string): Promise<ManagerCaseDetail> {
  return api.get<ManagerCaseDetail>(`/api/v1/cases/${caseId}/manager`);
}

/**
 * Change case status.
 */
export async function changeCaseStatus(
  caseId: string,
  request: ChangeStatusRequest
): Promise<CaseDetail> {
  return api.put<CaseDetail>(`/api/v1/cases/${caseId}/status`, request);
}

/**
 * Add internal message (only visible to managers).
 */
export async function addInternalMessage(
  caseId: string,
  userId: string,
  message: string
): Promise<InternalMessage> {
  return api.post<InternalMessage>(`/api/v1/cases/${caseId}/messages/internal`, {
    userId,
    message,
  });
}

/**
 * Add external message (visible to user).
 */
export async function addExternalMessage(
  caseId: string,
  userId: string,
  message: string
): Promise<ExternalMessage> {
  return api.post<ExternalMessage>(`/api/v1/cases/${caseId}/messages/external`, {
    userId,
    message,
  });
}
