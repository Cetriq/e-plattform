import { expect, test, type Page } from '@playwright/test';
import { BUILDING_PERMIT_FLOW, PERSONAS, expectAccessible, loginAs, settle, submittedCase } from './helpers';

/** Fill every visible field on the current form step with a plausible value. */
async function fillStep(page: Page) {
  await page.evaluate(() => {
    const set = (el: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement, value: string) => {
      const proto = Object.getPrototypeOf(el);
      Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
      for (const type of ['input', 'change', 'blur']) el.dispatchEvent(new Event(type, { bubbles: true }));
    };
    const main = document.querySelector('main') ?? document.body;
    for (const el of main.querySelectorAll<HTMLInputElement>('input, textarea, select')) {
      if (el.disabled || el.readOnly || el.offsetParent === null) continue;
      const type = (el.type || '').toLowerCase();
      const label = (el.labels?.[0]?.textContent ?? '').toLowerCase();
      if (el.tagName === 'SELECT') {
        const select = el as unknown as HTMLSelectElement;
        if (!select.value && select.options.length > 1) set(select, select.options[1].value);
      } else if (type === 'radio') {
        const group = main.querySelectorAll<HTMLInputElement>(`input[type=radio][name="${el.name}"]`);
        if (![...group].some((r) => r.checked)) el.click();
      } else if (type === 'checkbox') {
        if (!el.checked) el.click();
      } else if (type !== 'file' && !el.value) {
        set(el,
          type === 'email' || label.includes('e-post') ? 'test@example.com'
          : type === 'tel' || label.includes('telefon') ? '0701234567'
          : type === 'date' ? '2026-11-01'
          : type === 'number' ? '10'
          : label.includes('personnummer') ? '19121212-1212'
          : label.includes('postnummer') ? '12345'
          : 'Test');
      }
    }
  });
}

test('every step of the citizen form, and its error state', async ({ page }) => {
  await loginAs(page, PERSONAS.citizen);
  await page.goto(`/citizen/services/${BUILDING_PERMIT_FLOW}`);
  await settle(page);
  const start = page.getByRole('button', { name: /Starta|Börja/ });
  if (await start.count()) await start.first().click();

  // Continuing with empty required fields: errors are announced and focus moves to the first one
  await page.getByRole('button', { name: /^Nästa/ }).first().click();
  await expect(page.getByRole('alert').first()).toBeVisible();
  const focused = await page.evaluate(() => document.activeElement?.getAttribute('aria-invalid') === 'true'
    || !!document.activeElement?.closest('fieldset')?.querySelector('[role=alert], [id$="-error"]'));
  expect(focused, 'focus should move to the first invalid field').toBe(true);
  await expectAccessible(page);

  let steps = 0;
  for (; steps < 10; steps++) {
    await fillStep(page);
    await expectAccessible(page);
    const next = page.getByRole('button', { name: /^Nästa/ });
    if (!(await next.count())) break;
    await next.first().click();
    await page.waitForTimeout(500);
  }
  expect(steps, 'the form should be walked through to its last step').toBeGreaterThan(1);
});

test('dialogs work with the keyboard', async ({ page }) => {
  const caseId = await submittedCase();
  await loginAs(page, PERSONAS.manager);
  await page.goto(`/manager/cases/${caseId}`);
  await settle(page);

  const opener = page.getByRole('button', { name: 'Ändra status' }).first();
  await opener.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Ändra status' });
  await expect(dialog).toBeVisible();
  await expectAccessible(page);

  // Focus may leave for the browser UI but never reaches the page behind
  for (let i = 0; i < 20; i++) {
    await page.keyboard.press('Tab');
    const outside = await page.evaluate(() => {
      const active = document.activeElement;
      return !!active && active !== document.body && !document.querySelector('dialog[open]')?.contains(active);
    });
    expect(outside, 'focus escaped to the page behind the dialog').toBe(false);
  }

  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(opener).toBeFocused();
});

test('admin dialogs have no violations', async ({ page }) => {
  await loginAs(page, PERSONAS.admin);
  await page.goto(`/admin/flows/${BUILDING_PERMIT_FLOW}`);
  await settle(page);
  await page.getByRole('button', { name: /Lägg till fält/ }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expectAccessible(page);
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: 'Redigera fält' }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expectAccessible(page);
});

test('the admin action menu opens from the keyboard', async ({ page }) => {
  await loginAs(page, PERSONAS.admin);
  await page.goto('/admin/flows');
  await settle(page);
  const menuButton = page.getByRole('button', { name: /Fler åtgärder/ }).first();
  await menuButton.focus();
  await expect(menuButton.locator('xpath=following-sibling::div[1]')).toBeVisible();
});

test('pages are titled after their main heading', async ({ page }) => {
  await loginAs(page, PERSONAS.citizen);
  await page.goto('/citizen/cases');
  await settle(page);
  await expect(page).toHaveTitle('Mina ärenden – e-Plattform');
});

test.describe('reflow at 320 CSS pixels', () => {
  test.use({ viewport: { width: 320, height: 700 } });

  for (const [role, path] of [
    ['anonymous', '/'],
    ['citizen', '/citizen/services'],
    ['citizen', '/citizen/cases'],
    ['citizen', '/citizen/profile'],
    ['manager', '/manager/dashboard'],
  ] as const) {
    test(`${path} (${role}) has no horizontal scrolling`, async ({ page }) => {
      if (role !== 'anonymous') await loginAs(page, PERSONAS[role]);
      await page.goto(path);
      await settle(page);
      const width = await page.evaluate(() => document.documentElement.scrollWidth);
      expect(width).toBeLessThanOrEqual(320);
    });
  }
});
