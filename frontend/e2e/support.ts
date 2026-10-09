import { type Browser, type BrowserContext, type Page, expect } from '@playwright/test';

export type RoleKey = 'admin' | 'manager' | 'staff';

export interface Credentials {
  email: string;
  password: string;
}

export const CREDENTIALS: Record<RoleKey, Credentials> = {
  admin: { email: 'admin@seatwise.local', password: 'Admin#Seatwise1' },
  manager: { email: 'manager@seatwise.local', password: 'Manager#Seatwise1' },
  staff: { email: 'staff@seatwise.local', password: 'Staff#Seatwise1' },
};

/** The demo Staff user's name, as the app shows it in booking history. */
export const STAFF_NAME = 'Sam Taylor';

/** Where the setup project saves each role's browser session. */
export function authFile(role: RoleKey): string {
  return `e2e/.auth/${role}.json`;
}

/** Signs in through the Keycloak form. Leaves the page on the app, past the first load. */
export async function login(page: Page, role: RoleKey): Promise<void> {
  const { email, password } = CREDENTIALS[role];
  await page.goto('/');
  await page.waitForURL(/\/realms\/seatwise\//);
  await page.locator('#username').fill(email);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible();
}

/** A new browser context that is already signed in as `role`. */
export async function contextFor(browser: Browser, role: RoleKey): Promise<BrowserContext> {
  return browser.newContext({ storageState: authFile(role) });
}

/** Opens a page of the app as an already-signed-in user and waits for the shell to appear. */
export async function openApp(page: Page, path = '/'): Promise<void> {
  await page.goto(path);
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible();
}

export function randomSuffix(length = 6): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let out = '';
  for (let i = 0; i < length; i++) {
    out += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  return out;
}

/** yyyy-mm-dd, `daysAhead` days from today in the browser's local time. */
export function localDate(daysAhead: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export interface NewWorkshop {
  code: string;
  title: string;
  capacity: number;
}

export function newWorkshop(capacity: number): NewWorkshop {
  const code = `E2E-${randomSuffix()}`;
  return { code, title: `E2E Pottery ${code}`, capacity };
}

/** Fills the Schedule workshop form. The workshop is 3 days ahead, 10:00 to 11:00. */
export async function fillWorkshopForm(page: Page, workshop: NewWorkshop): Promise<void> {
  await page.getByLabel('Code', { exact: true }).fill(workshop.code);
  await page.getByLabel('Location', { exact: true }).selectOption({ index: 1 });
  await page.getByLabel('Title', { exact: true }).fill(workshop.title);
  await page.getByLabel('Instructor', { exact: true }).fill('E2E Instructor');
  await page.getByLabel('Date', { exact: true }).fill(localDate(3));
  await page.getByLabel('Start time', { exact: true }).fill('10:00');
  await page.getByLabel('End time', { exact: true }).fill('11:00');
  await page.getByLabel('Capacity').fill(String(workshop.capacity));
}

/**
 * Schedules a fresh workshop through the UI on a page that is already signed in as the Manager.
 * Returns the workshop's detail-page path.
 */
export async function scheduleOnPage(page: Page, workshop: NewWorkshop): Promise<string> {
  await openApp(page, '/workshops/new');
  await fillWorkshopForm(page, workshop);
  await page.getByRole('button', { name: 'Schedule workshop' }).click();
  await expect(page.getByRole('heading', { level: 1, name: workshop.title })).toBeVisible();
  return new URL(page.url()).pathname;
}

/**
 * Signs in as the Manager (in a throwaway context), schedules a fresh workshop and returns its
 * detail-page path. Each call makes a new workshop, so specs never depend on demo data.
 */
export async function scheduleWorkshop(
  browser: Browser,
  capacity: number,
): Promise<NewWorkshop & { path: string }> {
  const workshop = newWorkshop(capacity);
  const context = await contextFor(browser, 'manager');
  try {
    const path = await scheduleOnPage(await context.newPage(), workshop);
    return { ...workshop, path };
  } finally {
    await context.close();
  }
}

export function newAttendee(prefix: string): { name: string; email: string } {
  const tag = randomSuffix(5);
  return { name: `${prefix} ${tag}`, email: `e2e-${tag.toLowerCase()}@example.com` };
}

/** Fills the register form on a workshop page (does not submit). */
export async function fillRegisterForm(
  page: Page,
  attendee: { name: string; email: string },
): Promise<void> {
  await page.getByLabel('Attendee name').fill(attendee.name);
  await page.getByLabel('Email', { exact: true }).fill(attendee.email);
}

/** Registers one attendee and waits for the confirmation toast. */
export async function register(
  page: Page,
  attendee: { name: string; email: string },
): Promise<void> {
  await fillRegisterForm(page, attendee);
  await page.getByRole('button', { name: 'Register', exact: true }).click();
  await expect(page.getByText(`${attendee.name} is registered.`)).toBeVisible();
}
