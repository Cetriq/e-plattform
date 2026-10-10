import { test } from '@playwright/test';
import { BUILDING_PERMIT_FLOW, PERSONAS, expectAccessible, loginAs, settle, submittedCase } from './helpers';

/**
 * Every page type, for the role that uses it, scanned with axe (WCAG 2.1 AA).
 * Add new pages here.
 */
let caseId: string;
test.beforeAll(async () => {
  caseId = await submittedCase();
});

const pages: Record<keyof typeof PERSONAS | 'anonymous', (string | [string, () => string])[]> = {
  anonymous: ['/', '/auth/login', '/tillganglighet'],
  citizen: [
    '/citizen/services',
    `/citizen/services/${BUILDING_PERMIT_FLOW}`,
    '/citizen/cases',
    ['/citizen/cases/:id', () => `/citizen/cases/${caseId}`],
    '/citizen/profile',
  ],
  manager: ['/manager/dashboard', ['/manager/cases/:id', () => `/manager/cases/${caseId}`]],
  admin: [
    '/admin/flows',
    '/admin/flows/new',
    `/admin/flows/${BUILDING_PERMIT_FLOW}`,
    '/admin/categories',
    '/admin/statistics',
    '/admin/users',
  ],
  security: ['/security/audit', '/security/people', '/security/retention'],
  ops: ['/ops/status', '/ops/events'],
};

for (const [role, paths] of Object.entries(pages)) {
  test.describe(role, () => {
    for (const entry of paths) {
      // Paths that need test data are resolved when the test runs
      const [label, path] = typeof entry === 'string' ? [entry, () => entry] : entry;
      test(`${label.replace(/[0-9a-f-]{36}/g, ':id')} has no WCAG 2.1 AA violations`, async ({ page }) => {
        if (role !== 'anonymous') await loginAs(page, PERSONAS[role as keyof typeof PERSONAS]);
        await page.goto(path());
        await settle(page);
        await expectAccessible(page);
      });
    }
  });
}
