import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

export const API = process.env.API_URL ?? 'http://localhost:8080';

export const PERSONAS = {
  citizen: 'medborgare@example.com',
  manager: 'handlaggare@example.com',
  admin: 'admin@example.com',
  security: 'informationssakerhet@example.com',
  ops: 'it-drift@example.com',
} as const;

export const BUILDING_PERMIT_FLOW = '00000000-0000-0000-0004-000000000001';

export async function api<T = unknown>(method: string, path: string, token?: string, body?: unknown): Promise<T> {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

/** Log in through the API and hand the session to the page. Returns the token. */
export async function loginAs(page: Page, email: string): Promise<string> {
  const login = await api<{ token: string; user: unknown }>('POST', '/api/v1/public/auth/login', undefined, { email });
  await page.goto('/');
  await page.evaluate(([token, user]) => {
    localStorage.setItem('eplatform_token', token as string);
    localStorage.setItem('eplatform_user', JSON.stringify(user));
  }, [login.token, login.user]);
  return login.token;
}

/** A submitted case owned by the shared citizen, with a message on it. */
export async function submittedCase(): Promise<string> {
  const { token } = await api<{ token: string }>('POST', '/api/v1/public/auth/login', undefined, { email: PERSONAS.citizen });
  const created = await api<{ id: string }>('POST', '/api/v1/cases', token, { flowId: BUILDING_PERMIT_FLOW });
  await api('POST', `/api/v1/cases/${created.id}/submit`, token, {});
  await api('POST', `/api/v1/cases/${created.id}/messages`, token, { message: 'En fråga om min ansökan.' });
  return created.id;
}

/** Wait for client-side data to render before scanning. */
export async function settle(page: Page) {
  await page.waitForLoadState('networkidle');
  await page.locator('h1').first().waitFor();
}

/** Fail with a readable list if axe finds WCAG 2.1 A/AA violations. */
export async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const summary = results.violations.map(
    (v) => `${v.id} (${v.impact}): ${v.help}\n` + v.nodes.slice(0, 3).map((n) => `    ${n.target.join(' ')}`).join('\n')
  );
  expect(summary, summary.join('\n')).toEqual([]);
}
