import { type Page, expect, test } from '@playwright/test';

import { contextFor, fillRegisterForm, newAttendee, scheduleWorkshop } from './support';

const LAST_SEAT_MESSAGE = 'Sorry — the last seat was just taken by a colleague.';

/** True when this desk was told the last seat had gone. */
async function wasTurnedAway(page: Page): Promise<boolean> {
  return page.getByRole('alert').filter({ hasText: LAST_SEAT_MESSAGE }).isVisible();
}

test('two desks press Register at once for the last seat: one books, the other is offered the waitlist', async ({
  browser,
}) => {
  const workshop = await scheduleWorkshop(browser, 1);

  // Two independent browsers, both signed in as Staff, both looking at the same workshop.
  const deskA = await contextFor(browser, 'staff');
  const deskB = await contextFor(browser, 'staff');
  try {
    const pageA = await deskA.newPage();
    const pageB = await deskB.newPage();
    const attendeeA = newAttendee('Alice');
    const attendeeB = newAttendee('Bruno');

    await Promise.all([pageA.goto(workshop.path), pageB.goto(workshop.path)]);
    for (const page of [pageA, pageB]) {
      await expect(page.getByRole('heading', { level: 1, name: workshop.title })).toBeVisible();
      await expect(page.getByText('1 of 1 left')).toBeVisible();
    }

    await fillRegisterForm(pageA, attendeeA);
    await fillRegisterForm(pageB, attendeeB);

    // Both press Register in the same moment.
    await Promise.all([
      pageA.getByRole('button', { name: 'Register', exact: true }).click(),
      pageB.getByRole('button', { name: 'Register', exact: true }).click(),
    ]);

    // Each desk ends up with exactly one outcome: a booking, or the plain-language apology.
    const outcome = (page: Page, name: string) =>
      page
        .getByText(`${name} is registered.`)
        .or(page.getByRole('alert').filter({ hasText: LAST_SEAT_MESSAGE }));
    await expect(outcome(pageA, attendeeA.name)).toBeVisible();
    await expect(outcome(pageB, attendeeB.name)).toBeVisible();

    const aLost = await wasTurnedAway(pageA);
    const bLost = await wasTurnedAway(pageB);
    expect([aLost, bLost].filter(Boolean)).toHaveLength(1);
    const aWon = !aLost;

    const [winnerPage, loserPage, loser] = aWon
      ? [pageA, pageB, attendeeB]
      : [pageB, pageA, attendeeA];

    await expect(loserPage.getByRole('alert').filter({ hasText: LAST_SEAT_MESSAGE })).toBeVisible();
    await expect(winnerPage.getByRole('alert').filter({ hasText: LAST_SEAT_MESSAGE })).toHaveCount(
      0,
    );
    // No raw error codes in the copy.
    await expect(loserPage.getByText('WORKSHOP_FULL')).toHaveCount(0);

    // The seat count on the loser's screen catches up with the truth.
    await expect(loserPage.getByText('0 of 1 left')).toBeVisible();

    // The loser is offered the waitlist, and it works.
    const loserFirstName = loser.name.split(' ')[0];
    await loserPage
      .getByRole('button', { name: `Add ${loserFirstName} to the waitlist instead` })
      .click();
    await expect(loserPage.getByText(`${loser.name} is on the waitlist (position 1).`)).toBeVisible();

    const bookings = loserPage.locator('section[aria-labelledby="sw-history-h"]');
    const waitlisted = bookings.getByRole('row').filter({ hasText: loser.name });
    await expect(waitlisted).toContainText('Waitlist position 1');

    // Exactly one seat was ever sold.
    await expect(loserPage.getByText('1 person on the waitlist.')).toBeVisible();
    await expect(loserPage.getByText('0 of 1 left')).toBeVisible();
  } finally {
    await deskA.close();
    await deskB.close();
  }
});
