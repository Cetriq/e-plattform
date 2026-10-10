import { defineConfig, devices } from '@playwright/test';

/**
 * Accessibility tests (axe-core, WCAG 2.1 AA) against a running stack:
 * the frontend on WEB_URL and the API on API_URL, with the seeded demo data.
 * Run locally with `npm run test:a11y` once both are up.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.WEB_URL ?? 'http://localhost:3000',
    locale: 'sv-SE',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
