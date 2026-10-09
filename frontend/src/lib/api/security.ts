import { api } from './client';
import type { PaginatedResponse } from './manager';

/**
 * Information security and data protection (role SECURITY_OFFICER).
 */

export type AuditCategory = 'SECURITY' | 'DATA_ACCESS' | 'DATA_CHANGE' | 'ADMIN' | 'PRIVACY';
export type AuditOutcome = 'SUCCESS' | 'DENIED' | 'FAILURE';

export interface AuditEvent {
  seq: number;
  timestamp: string;
  userId: string;
  userName?: string;
  userEmail?: string;
  action: string;
  category: AuditCategory;
  outcome: AuditOutcome;
  entityType?: string;
  entityId?: string;
  subjectUserId?: string;
  details?: string;
  ipAddress?: string;
  requestMethod?: string;
  requestPath?: string;
  responseStatus?: number;
}

export interface AuditFilter {
  userId?: string;
  subjectId?: string;
  action?: string;
  category?: AuditCategory | '';
  outcome?: AuditOutcome | '';
  entityId?: string;
  from?: string;
  to?: string;
}

function query(filter: AuditFilter, extra: Record<string, string | number> = {}) {
  const params = new URLSearchParams();
  Object.entries({ ...filter, ...extra }).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params.set(key, String(value));
    }
  });
  return params.toString();
}

export async function searchAudit(filter: AuditFilter, page = 0, size = 50): Promise<PaginatedResponse<AuditEvent>> {
  return api.get(`/api/v1/security/audit?${query(filter, { page, size })}`);
}

export async function exportAudit(filter: AuditFilter, format: 'csv' | 'json'): Promise<void> {
  const date = new Date().toISOString().slice(0, 10);
  return api.downloadBlob(`/api/v1/security/audit/export?${query(filter, { format })}`, `sparbarhetslogg-${date}.${format}`);
}

export interface ChainResult {
  verified: number;
  intact: boolean;
  firstBrokenSeq?: number;
}

export async function verifyAudit(): Promise<ChainResult> {
  return api.get('/api/v1/security/audit/verify');
}

export interface PersonSummary {
  id: string;
  name: string;
  email: string;
  roles: string[];
  submittedCases: number;
  drafts: number;
  active: boolean;
}

export async function searchPeople(q: string): Promise<PersonSummary[]> {
  return api.get(`/api/v1/security/people?q=${encodeURIComponent(q)}`);
}

export async function downloadRegisterExtract(person: PersonSummary): Promise<void> {
  const date = new Date().toISOString().slice(0, 10);
  return api.downloadBlob(`/api/v1/security/people/${person.id}/extract`, `registerutdrag-${person.id.slice(0, 8)}-${date}.json`);
}

export interface RetainedCase {
  referenceNumber: string;
  flowName: string;
  status?: string;
  closedAt?: string;
  retentionMonths?: number;
  purgeAfter?: string;
}

export interface ErasurePreview {
  person: PersonSummary;
  allowed: boolean;
  notAllowedReason?: string;
  draftsToDelete: number;
  retainedCases: RetainedCase[];
}

export async function getErasurePreview(userId: string): Promise<ErasurePreview> {
  return api.get(`/api/v1/security/people/${userId}/erasure`);
}

export async function erasePerson(userId: string): Promise<{ draftsDeleted: number; casesRetained: number }> {
  return api.post(`/api/v1/security/people/${userId}/erase`);
}

export interface DueCase {
  caseId: string;
  referenceNumber: string;
  ownerId: string;
  flowName: string;
  purgeAfter: string;
}

export interface RetentionOverview {
  draftDays: number;
  auditMonths: number;
  dueWithin30Days: DueCase[];
}

export async function getRetention(): Promise<RetentionOverview> {
  return api.get('/api/v1/security/retention');
}

export async function runRetention(): Promise<{
  casesPurged: number;
  draftsPurged: number;
  auditEntriesPurged: number;
  systemEventsPurged: number;
}> {
  return api.post('/api/v1/security/retention/run');
}

/** Plain-language names for the logged actions. */
export const ACTION_LABELS: Record<string, string> = {
  LOGIN_SUCCESS: 'Inloggning',
  LOGIN_FAILURE: 'Misslyckad inloggning',
  DEMO_ACCOUNT_CREATED: 'Demokonto skapat',
  ACCESS_DENIED: 'Nekad åtkomst',
  RATE_LIMIT_EXCEEDED: 'För många anrop',
  CASE_VIEW: 'Läste ärende',
  CASE_LIST: 'Listade ärenden',
  CASE_SEARCH: 'Sökte ärenden',
  CASE_EXPORT_PDF: 'Laddade ner ärende (PDF)',
  MESSAGE_VIEW: 'Läste meddelanden',
  FILE_DOWNLOAD: 'Laddade ner fil',
  FILE_VIEW: 'Visade fil',
  USER_LIST: 'Visade användare',
  PROFILE_VIEW: 'Visade profil',
  CASE_CREATE: 'Skapade ärende',
  CASE_UPDATE: 'Ändrade svar',
  CASE_SUBMIT: 'Skickade in ärende',
  CASE_DELETE: 'Raderade utkast',
  CASE_STATUS_CHANGE: 'Ändrade status',
  CASE_ASSIGN: 'Tilldelade ärende',
  MESSAGE_SEND: 'Skickade meddelande',
  NOTE_ADD: 'Intern anteckning',
  FILE_UPLOAD: 'Laddade upp fil',
  FILE_DELETE: 'Tog bort fil',
  PROFILE_UPDATE: 'Ändrade profil',
  FLOW_CHANGE: 'Ändrade e-tjänst',
  CATEGORY_CHANGE: 'Ändrade kategori',
  USER_ROLE_CHANGE: 'Ändrade roller',
  AUDIT_VIEW: 'Sökte i spårbarhetsloggen',
  AUDIT_EXPORT: 'Exporterade spårbarhetsloggen',
  AUDIT_VERIFY: 'Kontrollerade loggens integritet',
  REGISTER_EXTRACT: 'Tog fram registerutdrag',
  USER_ERASED: 'Raderade personuppgifter',
  CASE_PURGED: 'Gallrade ärende',
  DRAFT_PURGED: 'Gallrade utkast',
};

export const CATEGORY_LABELS: Record<AuditCategory, string> = {
  SECURITY: 'Säkerhet',
  DATA_ACCESS: 'Läsning',
  DATA_CHANGE: 'Ändring',
  ADMIN: 'Administration',
  PRIVACY: 'Dataskydd',
};

export const OUTCOME_LABELS: Record<AuditOutcome, string> = {
  SUCCESS: 'Lyckad',
  DENIED: 'Nekad',
  FAILURE: 'Fel',
};
