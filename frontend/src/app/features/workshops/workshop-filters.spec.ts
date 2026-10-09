import { convertToParamMap } from '@angular/router';

import {
  defaultFilters,
  filtersFromParams,
  filtersToParams,
  resolveDateRange,
  toIsoDate,
  toSearchQuery,
  weekRange,
} from './workshop-filters';

const params = (record: Record<string, string | string[]>) => convertToParamMap(record);

describe('workshop filters', () => {
  describe('defaults', () => {
    it('opens on "this week, has seats" when the address has no parameters', () => {
      expect(filtersFromParams(params({}))).toEqual({
        preset: 'this-week',
        from: null,
        to: null,
        statuses: [],
        locationId: null,
        hasSeats: true,
        q: '',
        page: 0,
      });
    });

    it('does not force "has seats" back on once other parameters are present', () => {
      const filters = filtersFromParams(params({ preset: 'today' }));

      expect(filters.preset).toBe('today');
      expect(filters.hasSeats).toBe(false);
    });

    it('writes the default into explicit parameters', () => {
      expect(filtersToParams(defaultFilters())).toEqual({ preset: 'this-week', hasSeats: 'true' });
    });
  });

  describe('URL parameters', () => {
    it('reads every filter from the address', () => {
      const filters = filtersFromParams(
        params({
          preset: 'custom',
          from: '2030-10-01',
          to: '2030-10-31',
          status: ['OPEN', 'FULL'],
          locationId: 'loc-2',
          hasSeats: 'true',
          q: ' pottery ',
          page: '2',
        }),
      );

      expect(filters).toEqual({
        preset: 'custom',
        from: '2030-10-01',
        to: '2030-10-31',
        statuses: ['OPEN', 'FULL'],
        locationId: 'loc-2',
        hasSeats: true,
        q: 'pottery',
        page: 2,
      });
    });

    it('writes status as a repeated parameter and leaves empty filters out', () => {
      const written = filtersToParams({
        preset: 'next-7-days',
        from: null,
        to: null,
        statuses: ['OPEN', 'IN_PROGRESS'],
        locationId: null,
        hasSeats: false,
        q: '',
        page: 0,
      });

      expect(written).toEqual({ preset: 'next-7-days', status: ['OPEN', 'IN_PROGRESS'] });
    });

    it('round-trips a full set of filters', () => {
      const original = {
        preset: 'custom' as const,
        from: '2030-10-01',
        to: '2030-10-05',
        statuses: ['FULL' as const, 'CANCELLED' as const],
        locationId: 'loc-3',
        hasSeats: true,
        q: 'glaze',
        page: 3,
      };
      const written = filtersToParams(original);

      expect(filtersFromParams(params(written as Record<string, string | string[]>))).toEqual(
        original,
      );
    });

    it('only keeps from/to for the custom preset', () => {
      const filters = filtersFromParams(params({ preset: 'today', from: '2030-10-01' }));

      expect(filters.from).toBeNull();
      expect(filtersToParams({ ...defaultFilters(), preset: 'today', from: '2030-10-01' })).toEqual({
        preset: 'today',
        hasSeats: 'true',
      });
    });

    it('treats bare from/to as a custom range', () => {
      expect(filtersFromParams(params({ from: '2030-10-01', to: '2030-10-02' })).preset).toBe(
        'custom',
      );
    });

    it('ignores junk instead of trusting it', () => {
      const filters = filtersFromParams(
        params({ preset: 'forever', status: ['NOPE', 'OPEN', 'OPEN'], page: '-4', from: 'soon' }),
      );

      expect(filters.preset).toBe('this-week');
      expect(filters.statuses).toEqual(['OPEN']);
      expect(filters.page).toBe(0);
      expect(filters.from).toBeNull();
    });
  });

  describe('date math (local calendar)', () => {
    it('gives Monday to Sunday for a mid-week day', () => {
      // Friday 9 Oct 2026
      expect(weekRange(new Date(2026, 9, 9, 14, 30))).toEqual({
        from: '2026-10-05',
        to: '2026-10-11',
      });
    });

    it('keeps Sunday in the week that started the Monday before', () => {
      expect(weekRange(new Date(2026, 9, 11, 23, 59))).toEqual({
        from: '2026-10-05',
        to: '2026-10-11',
      });
    });

    it('starts a new week on Monday', () => {
      expect(weekRange(new Date(2026, 9, 12, 0, 0))).toEqual({
        from: '2026-10-12',
        to: '2026-10-18',
      });
    });

    it('crosses month and year ends', () => {
      expect(weekRange(new Date(2026, 11, 31))).toEqual({ from: '2026-12-28', to: '2027-01-03' });
    });

    it('resolves the presets', () => {
      const now = new Date(2026, 9, 9, 8, 0);

      expect(resolveDateRange({ preset: 'today', from: null, to: null }, now)).toEqual({
        from: '2026-10-09',
        to: '2026-10-09',
      });
      expect(resolveDateRange({ preset: 'next-7-days', from: null, to: null }, now)).toEqual({
        from: '2026-10-09',
        to: '2026-10-15',
      });
      expect(resolveDateRange({ preset: 'custom', from: '2026-11-01', to: null }, now)).toEqual({
        from: '2026-11-01',
        to: null,
      });
    });

    it('formats the local day, not the UTC day', () => {
      expect(toIsoDate(new Date(2026, 0, 5, 0, 5))).toBe('2026-01-05');
    });
  });

  it('turns filters into a search query', () => {
    const query = toSearchQuery(
      { ...defaultFilters(), statuses: ['OPEN'], q: 'clay', page: 1, locationId: 'loc-1' },
      new Date(2026, 9, 9),
    );

    expect(query).toEqual({
      from: '2026-10-05',
      to: '2026-10-11',
      statuses: ['OPEN'],
      locationId: 'loc-1',
      hasSeats: true,
      q: 'clay',
      page: 1,
      size: 20,
    });
  });
});
