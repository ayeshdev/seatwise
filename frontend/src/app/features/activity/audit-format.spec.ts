import { formatDateTime } from '@features/workshops/workshop-format';

import { actionLabel, actionTone, fieldLabel, formatChangeValue, formatRelative } from './audit-format';

describe('audit-format', () => {
  describe('fieldLabel', () => {
    it('uses friendly names for the known fields', () => {
      expect(fieldLabel('capacity')).toBe('Capacity');
      expect(fieldLabel('startsAt')).toBe('Starts');
      expect(fieldLabel('endsAt')).toBe('Ends');
      expect(fieldLabel('fullName')).toBe('Full name');
      expect(fieldLabel('active')).toBe('Account status');
      expect(fieldLabel('role')).toBe('Role');
    });

    it('spells out an unknown field instead of showing it raw', () => {
      expect(fieldLabel('seatsTaken')).toBe('Seats taken');
      expect(fieldLabel('some_other-field')).toBe('Some other field');
    });
  });

  describe('formatChangeValue', () => {
    it('shows dates as dates, not ISO strings', () => {
      const iso = new Date(2030, 9, 17, 9, 30).toISOString();
      expect(formatChangeValue('startsAt', iso)).toBe(formatDateTime(iso));
      expect(formatChangeValue('startsAt', iso)).not.toMatch(/\d{4}-\d{2}-\d{2}T/);
    });

    it('shows booleans as words', () => {
      expect(formatChangeValue('active', true)).toBe('Active');
      expect(formatChangeValue('active', false)).toBe('Inactive');
      expect(formatChangeValue('something', true)).toBe('Yes');
      expect(formatChangeValue('something', false)).toBe('No');
    });

    it('shows roles by their screen name', () => {
      expect(formatChangeValue('role', 'MANAGER')).toBe('Manager');
      expect(formatChangeValue('role', 'ADMIN')).toBe('Admin');
    });

    it('shows an empty value as "Not set"', () => {
      expect(formatChangeValue('description', null)).toBe('Not set');
      expect(formatChangeValue('description', undefined)).toBe('Not set');
      expect(formatChangeValue('description', '')).toBe('Not set');
    });

    it('keeps numbers and text as they are', () => {
      expect(formatChangeValue('capacity', 16)).toBe('16');
      expect(formatChangeValue('capacity', 0)).toBe('0');
      expect(formatChangeValue('title', 'Wheel-throwing')).toBe('Wheel-throwing');
    });
  });

  describe('actions', () => {
    it('reads in plain words with a tone', () => {
      expect(actionLabel('ROLE_CHANGED')).toBe('Role changed');
      expect(actionLabel('PROMOTED')).toBe('Moved off the waitlist');
      expect(actionTone('CANCELLED')).toBe('full');
      expect(actionTone('DEACTIVATED')).toBe('full');
      expect(actionTone('PROMOTED')).toBe('ok');
      expect(actionTone('REACTIVATED')).toBe('ok');
      expect(actionTone('CREATED')).toBe('neutral');
      expect(actionTone('UPDATED')).toBe('neutral');
    });

    it('copes with an action it has never heard of', () => {
      expect(actionLabel('SOMETHING_NEW')).toBe('Something new');
      expect(actionTone('SOMETHING_NEW')).toBe('neutral');
    });
  });

  describe('formatRelative', () => {
    const now = new Date('2030-10-17T12:00:00Z');
    const ago = (ms: number) => new Date(now.getTime() - ms).toISOString();

    it('says "just now" for the last moments', () => {
      expect(formatRelative(ago(10_000), now, 'en')).toBe('just now');
    });

    it('counts minutes, hours and days', () => {
      expect(formatRelative(ago(5 * 60_000), now, 'en')).toBe('5 minutes ago');
      expect(formatRelative(ago(3 * 3_600_000), now, 'en')).toBe('3 hours ago');
      expect(formatRelative(ago(24 * 3_600_000), now, 'en')).toBe('yesterday');
      expect(formatRelative(ago(4 * 24 * 3_600_000), now, 'en')).toBe('4 days ago');
    });
  });
});
