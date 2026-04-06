import { api } from './client';
import type { CaseSummary, CaseDetail, QueryInstance } from './cases';

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

/**
 * Download case as PDF.
 */
export async function downloadCasePdf(caseId: string): Promise<void> {
  const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
  const response = await fetch(`${API_BASE_URL}/api/v1/cases/${caseId}/pdf`, {
    headers: {
      // Don't set Content-Type for downloads
    },
  });

  if (!response.ok) {
    throw new Error('Failed to download PDF');
  }

  // Get filename from Content-Disposition header or use default
  const contentDisposition = response.headers.get('Content-Disposition');
  let filename = `case-${caseId}.pdf`;
  if (contentDisposition) {
    const match = contentDisposition.match(/filename="?([^"]+)"?/);
    if (match) {
      filename = match[1];
    }
  }

  // Create blob and trigger download
  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}
