import { expect, test } from '@playwright/test';

import {
  STAFF_NAME,
  authFile,
  fillRegisterForm,
  newAttendee,
  openApp,
  register,
  scheduleWorkshop,
} from './support';

test.use({ storageState: authFile('staff') });

test.describe('Staff', () => {
  test('lands on "This week, has seats" without any way to schedule or edit', async ({
    page,
    browser,
  }) => {
    const workshop = await scheduleWorkshop(browser, 2);

    await openApp(page);
    await expect(page).toHaveURL(/\/workshops\?/);
    await expect(page).toHaveURL(/preset=this-week/);
    await expect(page).toHaveURL(/hasSeats=true/);

    await expect(page.getByRole('heading', { level: 1, name: 'Workshops' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'This week' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(page.getByRole('switch', { name: 'Seats' })).toBeChecked();

    // Nothing here offers scheduling.
    await expect(page.getByRole('button', { name: 'Schedule workshop' })).toHaveCount(0);

    // On a workshop, Staff can register attendees but cannot edit or cancel the workshop.
    await page.goto(workshop.path);
    await expect(page.getByRole('heading', { level: 1, name: workshop.title })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Register', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Edit', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cancel workshop' })).toHaveCount(0);

    // Typing the edit address by hand sends Staff back to Workshops.
    await page.goto(`${workshop.path}/edit`);
    await expect(page).toHaveURL(/\/workshops(\?|$)/);
    await expect(page.getByRole('heading', { name: 'Edit workshop' })).toHaveCount(0);
  });

  test('registers an attendee, then cancels with a reason and sees who cancelled', async ({
    page,
    browser,
  }) => {
    const workshop = await scheduleWorkshop(browser, 2);
    const attendee = newAttendee('Priya');

    await openApp(page, workshop.path);
    await expect(page.getByText('2 of 2 left')).toBeVisible();

    await register(page, attendee);
    await expect(page.getByText('1 of 2 left')).toBeVisible();

    const bookings = page.locator('section[aria-labelledby="sw-history-h"]');
    const row = bookings.getByRole('row').filter({ hasText: attendee.name });
    await expect(row).toContainText(`Registered by ${STAFF_NAME}`);

    await row.getByRole('button', { name: `Cancel booking for ${attendee.name}` }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: new RegExp(attendee.name) })).toBeVisible();
    await dialog.getByLabel('Reason (optional)').fill('Caller changed their mind');
    await dialog.getByRole('button', { name: 'Cancel booking' }).click();

    await expect(page.getByText(`${attendee.name}'s booking is cancelled.`)).toBeVisible();
    await expect(row).toContainText(`Cancelled by ${STAFF_NAME}`);
    await expect(row).toContainText('Caller changed their mind');
    // The seat is free again and the row stays in the history.
    await expect(page.getByText('2 of 2 left')).toBeVisible();
  });

  test('cannot change a workshop through the API either', async ({ page, browser }) => {
    const workshop = await scheduleWorkshop(browser, 2);
    const workshopId = workshop.path.split('/').pop();

    const tokenRequest = page.waitForRequest(
      async (request) =>
        request.url().includes('/api/') &&
        (await request.allHeaders())['authorization'] !== undefined,
    );
    await openApp(page, workshop.path);
    const authorization = (await (await tokenRequest).allHeaders())['authorization'];

    const response = await page.request.put(`/api/v1/workshops/${workshopId}`, {
      headers: { authorization },
      data: { title: 'Hacked' },
    });
    expect(response.status()).toBe(403);
  });

  test('a duplicate booking is explained in plain words', async ({ page, browser }) => {
    const workshop = await scheduleWorkshop(browser, 2);
    const attendee = newAttendee('Dana');

    await openApp(page, workshop.path);
    await register(page, attendee);

    await fillRegisterForm(page, attendee);
    await page.getByRole('button', { name: 'Register', exact: true }).click();
    await expect(
      page.getByText('This person is already booked (or waitlisted) on this workshop.'),
    ).toBeVisible();
  });
});
