import {
  firstName,
  formatDateTime,
  formatTimeRange,
  formatWhen,
  localDateValue,
  localTimeValue,
  toIsoInstant,
} from './workshop-format';

describe('workshop formatting', () => {
  const starts = new Date(2026, 9, 17, 9, 30).toISOString();
  const ends = new Date(2026, 9, 17, 12, 0).toISOString();

  it('shows a same-day range the way staff say it', () => {
    expect(formatWhen(starts, ends, 'en-GB')).toBe('Sat 17 Oct, 09:30–12:00');
  });

  it('shows the end date when a workshop runs past midnight', () => {
    const late = new Date(2026, 9, 17, 22, 0).toISOString();
    const after = new Date(2026, 9, 18, 1, 0).toISOString();

    expect(formatTimeRange(late, after, 'en-GB')).toBe('22:00 – Sun 18 Oct, 01:00');
  });

  it('shows a single moment with its date', () => {
    expect(formatDateTime(starts, 'en-GB')).toBe('Sat 17 Oct, 09:30');
  });

  it('never leaks the raw ISO string', () => {
    expect(formatWhen(starts, ends)).not.toContain('T');
    expect(formatWhen(starts, ends)).not.toContain('Z');
  });

  it('round-trips local date and time through an ISO instant', () => {
    const iso = toIsoInstant('2026-10-17', '09:30');

    expect(iso).toBe(starts);
    expect(localDateValue(starts)).toBe('2026-10-17');
    expect(localTimeValue(starts)).toBe('09:30');
  });

  it('refuses unusable input', () => {
    expect(toIsoInstant('', '09:30')).toBeNull();
    expect(toIsoInstant('2026-10-17', '')).toBeNull();
  });

  it('takes the first name for friendly buttons', () => {
    expect(firstName('  Priya Shah ')).toBe('Priya');
    expect(firstName('Priya')).toBe('Priya');
  });
});
