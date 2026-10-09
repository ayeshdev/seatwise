import { expect, test } from '@playwright/test';

test('signs in through Keycloak and shows the admin shell', async ({ page }) => {
  await page.goto('/');

  // The app requires sign-in, so the browser is sent to the Keycloak login page.
  await page.waitForURL(/\/realms\/seatwise\//);

  await page.locator('#username').fill('admin@seatwise.local');
  await page.locator('#password').fill('Admin#Seatwise1');
  await page.locator('#kc-login').click();

  // Back in the app: the Admin lands on Staff accounts with the Admin navigation.
  await expect(page.getByRole('link', { name: 'Seatwise' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Staff accounts' })).toBeVisible();
  await expect(page).toHaveURL(/\/staff-accounts$/);
});
