import { api } from './client';
import type { PaginatedResponse } from './manager';

/**
 * IT and operations (role OPERATIONS). Contains no personal data.
 */

export type EventLevel = 'INFO' | 'WARN' | 'ERROR';

export interface SystemEvent {
  id: string;
  timestamp: string;
  level: EventLevel;
  source: string;
  message: string;
  details: Record<string, unknown>;
}

export interface OpsStatus {
  overall: 'UP' | 'DOWN';
  version: string;
  profiles: string;
  instanceStartedAt: string;
  uptimeSeconds: number;
  heapUsedMb: number;
  heapMaxMb: number;
  components: { name: string; status: 'UP' | 'DOWN' | 'DISABLED'; detail: string }[];
  requests: { count: number; serverErrors: number; meanMs: number; p95Ms: number };
  errorsLast24h: number;
  lastJobRuns: SystemEvent[];
  recentStartups: SystemEvent[];
}

export async function getOpsStatus(): Promise<OpsStatus> {
  return api.get('/api/v1/ops/status');
}

export async function getSystemEvents(level: EventLevel | '', source: string, page = 0): Promise<PaginatedResponse<SystemEvent>> {
  const params = new URLSearchParams({ page: String(page), size: '50', source });
  if (level) params.set('level', level);
  return api.get(`/api/v1/ops/events?${params}`);
}
