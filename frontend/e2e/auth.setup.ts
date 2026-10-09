import { test as setup } from '@playwright/test';

import { type RoleKey, authFile, login } from './support';

// One sign-in per role; the specs reuse the saved sessions instead of logging in again.
const roles: RoleKey[] = ['admin', 'manager', 'staff'];

for (const role of roles) {
  setup(`sign in as ${role}`, async ({ page }) => {
    await login(page, role);
    await page.context().storageState({ path: authFile(role) });
  });
}
