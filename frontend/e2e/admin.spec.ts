import { expect, test } from '@playwright/test';

import { authFile, openApp, randomSuffix } from './support';

test.use({ storageState: authFile('admin') });

test.describe('Admin', () => {
  test('lands on Staff accounts and has no Workshops in the navigation', async ({ page }) => {
    await openApp(page);

    await expect(page).toHaveURL(/\/staff-accounts$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Staff accounts' })).toBeVisible();

    const nav = page.getByRole('navigation', { name: 'Main' });
    await expect(nav.getByRole('link', { name: 'Staff accounts' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Workshops' })).toHaveCount(0);
  });

  test('creates a Front desk account with a generated password and sees it in the list', async ({
    page,
  }) => {
    const tag = randomSuffix(5);
    const fullName = `Aaa E2E Desk ${tag}`;
    const email = `e2e-desk-${tag.toLowerCase()}@example.com`;

    await openApp(page, '/staff-accounts/new');
    await expect(page.getByRole('heading', { level: 1, name: 'Add staff member' })).toBeVisible();

    await page.getByLabel('Full name').fill(fullName);
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.getByRole('radio', { name: /Front desk/ }).check();

    await page.getByRole('button', { name: 'Generate' }).click();
    await expect(page.getByLabel('Temporary password')).not.toHaveValue('');

    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page.getByText(`Account created for ${fullName}.`)).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: fullName })).toBeVisible();

    // Back on the list the new person appears with the plain-language role name.
    await openApp(page, '/staff-accounts');
    const row = page.getByRole('row').filter({ hasText: fullName });
    await expect(row).toBeVisible();
    await expect(row).toContainText(email);
    await expect(row).toContainText('Front desk');

    // Tidy up so repeated runs don't fill the first page: deactivated accounts drop off the list.
    await row.getByRole('link', { name: fullName }).click();
    await page.getByRole('button', { name: 'Deactivate account' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Deactivate account' }).click();
    await openApp(page, '/staff-accounts');
    await expect(page.getByRole('row').filter({ hasText: fullName })).toHaveCount(0);
  });

  test('is turned away from /workshops and the API refuses the Admin token with 403', async ({
    page,
  }) => {
    // Keep the bearer token the app itself sends to the API.
    const tokenRequest = page.waitForRequest(
      async (request) => {
        if (!request.url().includes('/api/')) {
          return false;
        }
        return (await request.allHeaders())['authorization'] !== undefined;
      },
      { timeout: 30_000 },
    );

    await openApp(page);
    const authorization = (await (await tokenRequest).allHeaders())['authorization'];
    expect(authorization).toMatch(/^Bearer /);

    // The role guard sends the Admin back to their own landing page.
    await page.goto('/workshops');
    await expect(page).toHaveURL(/\/staff-accounts$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Staff accounts' })).toBeVisible();

    // The guard is only clarity: the API refuses regardless.
    const response = await page.request.get('/api/v1/workshops', {
      headers: { authorization },
    });
    expect(response.status()).toBe(403);
  });
});
