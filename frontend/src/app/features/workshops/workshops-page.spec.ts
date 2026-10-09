import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Role } from '@core/auth/role';

import { weekRange } from './workshop-filters';
import { formatDay, formatTimeRange } from './workshop-format';
import { API, makeSummary, provideApiTesting } from './workshop.fixtures';
import { WorkshopSearchResult } from './workshop.models';
import { WorkshopsPage } from './workshops-page';

@Component({ selector: 'sw-stub', template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class Stub {}

const LOCATIONS = [
  { id: 'loc-1', name: 'Riverside' },
  { id: 'loc-2', name: 'Hilltop' },
];

function result(overrides: Partial<WorkshopSearchResult> = {}): WorkshopSearchResult {
  return { items: [makeSummary()], page: 0, size: 20, totalItems: 1, searchMode: 'index', ...overrides };
}

describe('WorkshopsPage', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let root: HTMLElement;

  const isSearch = (r: HttpRequest<unknown>) => r.url === `${API}/workshops`;

  async function open(role: Role, url = '/workshops'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        ...provideApiTesting(role).providers,
        provideRouter([
          { path: 'workshops', component: WorkshopsPage },
          { path: 'workshops/new', component: Stub },
          { path: 'workshops/:id', component: Stub },
        ]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, WorkshopsPage);
    root = harness.routeNativeElement as HTMLElement;
    harness.detectChanges();
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  /** Answers the location lookup and the latest search. */
  async function answer(searchResult: WorkshopSearchResult = result()): Promise<TestRequest> {
    http.expectOne(`${API}/locations`).flush(LOCATIONS);
    const search = http.expectOne(isSearch);
    search.flush(searchResult);
    await settle();
    return search;
  }

  function button(label: string): HTMLButtonElement {
    const found = Array.from(root.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    );
    if (!found) {
      throw new Error(`No button "${label}"`);
    }
    return found;
  }

  afterEach(() => {
    http.verify();
    jest.useRealTimers();
  });

  describe('defaults and the address bar', () => {
    it('opens on "this week, has seats" and says so in the URL', async () => {
      await open('STAFF');
      const search = await answer();

      const week = weekRange(new Date());
      expect(search.request.params.get('from')).toBe(week.from);
      expect(search.request.params.get('to')).toBe(week.to);
      expect(search.request.params.get('hasSeats')).toBe('true');
      expect(search.request.params.has('status')).toBe(false);
      expect(search.request.params.get('page')).toBe('0');
      expect(router.url).toBe('/workshops?preset=this-week&hasSeats=true');
      expect(button('This week').getAttribute('aria-pressed')).toBe('true');
      expect(root.querySelector('[role="switch"]')?.getAttribute('aria-checked')).toBe('true');
    });

    it('turns every parameter into the search request', async () => {
      await open(
        'MANAGER',
        '/workshops?preset=custom&from=2030-10-01&to=2030-10-05&status=OPEN&status=FULL&locationId=loc-1&hasSeats=true&q=clay&page=1',
      );
      const search = await answer(result({ page: 1, totalItems: 25 }));

      const p = search.request.params;
      expect(p.get('from')).toBe('2030-10-01');
      expect(p.get('to')).toBe('2030-10-05');
      expect(p.getAll('status')).toEqual(['OPEN', 'FULL']);
      expect(p.get('locationId')).toBe('loc-1');
      expect(p.get('hasSeats')).toBe('true');
      expect(p.get('q')).toBe('clay');
      expect(p.get('page')).toBe('1');
      expect((root.querySelector('#sw-filter-search') as HTMLInputElement).value).toBe('clay');
      expect((root.querySelector('#sw-filter-location') as HTMLSelectElement).value).toBe('loc-1');
    });

    it('changes the URL when a preset, status or the seats toggle changes', async () => {
      await open('STAFF');
      await answer();

      button('Today').click();
      await settle();
      expect(router.url).toBe('/workshops?preset=today&hasSeats=true');
      http.expectOne(isSearch).flush(result());

      button('Full').click();
      await settle();
      expect(router.url).toBe('/workshops?preset=today&status=FULL&hasSeats=true');
      const second = http.expectOne(isSearch);
      expect(second.request.params.getAll('status')).toEqual(['FULL']);
      second.flush(result());

      (root.querySelector('[role="switch"]') as HTMLButtonElement).click();
      await settle();
      expect(router.url).toBe('/workshops?preset=today&status=FULL');
      http.expectOne(isSearch).flush(result());
    });

    it('seeds the custom dates from the range being shown', async () => {
      await open('STAFF');
      await answer();

      button('Custom').click();
      await settle();

      const week = weekRange(new Date());
      expect(router.url).toBe(
        `/workshops?preset=custom&from=${week.from}&to=${week.to}&hasSeats=true`,
      );
      http.expectOne(isSearch).flush(result());
      expect((root.querySelector('#sw-filter-from') as HTMLInputElement).value).toBe(week.from);
    });

    it('waits 200 ms after the last keystroke before searching', async () => {
      await open('STAFF');
      await answer();
      const navigate = jest.spyOn(router, 'navigate');

      jest.useFakeTimers({ doNotFake: ['nextTick', 'setImmediate', 'queueMicrotask'] });
      const box = root.querySelector('#sw-filter-search') as HTMLInputElement;
      box.value = 'pot';
      box.dispatchEvent(new Event('input'));
      jest.advanceTimersByTime(150);
      box.value = 'potery';
      box.dispatchEvent(new Event('input'));
      jest.advanceTimersByTime(199);
      expect(navigate).not.toHaveBeenCalled();

      jest.advanceTimersByTime(1);
      expect(navigate).toHaveBeenCalledTimes(1);
      jest.useRealTimers();
      await settle();
      expect(router.url).toContain('q=potery');
      http.expectOne(isSearch).flush(result());
    });
  });

  describe('results', () => {
    it('shows each workshop with its seats, status and a link to the detail page', async () => {
      await open('STAFF');
      await answer(
        result({
          items: [
            makeSummary(),
            makeSummary({ id: 'w-2', code: 'GLZ-7', title: 'Glazing', seatsLeft: 0, status: 'FULL' }),
          ],
          totalItems: 2,
        }),
      );

      const rows = root.querySelectorAll('tbody tr');
      expect(rows).toHaveLength(2);
      expect(rows[0].textContent).toContain('POT-0412');
      expect(rows[0].textContent).toContain('Wheel-throwing for beginners');
      expect(rows[0].textContent).toContain('Amara Silva');
      expect(rows[0].textContent).toContain('Riverside');
      expect(rows[0].textContent).toContain('3 of 20 left');
      expect(rows[0].textContent).toContain('Open');
      expect(rows[0].textContent).toContain(formatDay(makeSummary().startsAt));
      expect(rows[0].textContent).toContain(
        formatTimeRange(makeSummary().startsAt, makeSummary().endsAt),
      );
      expect(rows[0].textContent).not.toContain('2030-');
      expect(rows[1].textContent).toContain('0 of 20 left');
      expect(rows[1].textContent).toContain('Full');
      expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/workshops/w-1');
    });

    it('shows a loading skeleton until the first answer', async () => {
      await open('STAFF');

      expect(root.querySelector('[aria-label="Loading workshops"]')).not.toBeNull();
      await answer();
      expect(root.querySelector('[aria-label="Loading workshops"]')).toBeNull();
    });

    it('explains an empty result and how to widen it', async () => {
      await open('STAFF');
      await answer(result({ items: [], totalItems: 0 }));

      expect(root.textContent).toContain('No workshops match.');
      expect(root.textContent).toContain('Try a wider date range.');
      expect(root.querySelector('table')).toBeNull();
    });

    it('says when search is in basic mode, and only then', async () => {
      await open('STAFF');
      await answer(result({ searchMode: 'fallback' }));

      expect(root.textContent).toContain('Search is running in basic mode — exact words only.');
    });

    it('stays quiet when the index is serving search', async () => {
      await open('STAFF');
      await answer();

      expect(root.textContent).not.toContain('basic mode');
    });

    it('offers a retry when the search fails', async () => {
      await open('STAFF');
      http.expectOne(`${API}/locations`).flush(LOCATIONS);
      http
        .expectOne(isSearch)
        .flush({ code: 'VALIDATION_FAILED' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(root.textContent).toContain("We couldn't load the workshops");
      button('Try again').click();
      await settle();
      http.expectOne(isSearch).flush(result());
      await settle();
      expect(root.querySelector('tbody tr')).not.toBeNull();
    });

    it('moves to the next page through the URL', async () => {
      await open('STAFF');
      await answer(result({ totalItems: 45 }));

      button('Next').click();
      await settle();

      expect(router.url).toContain('page=1');
      const next = http.expectOne(isSearch);
      expect(next.request.params.get('page')).toBe('1');
      next.flush(result({ page: 1, totalItems: 45 }));
    });
  });

  describe('role-aware actions', () => {
    it('shows "Schedule workshop" to a manager', async () => {
      await open('MANAGER');
      await answer();

      expect(root.textContent).toContain('Schedule workshop');
    });

    it('hides "Schedule workshop" from staff', async () => {
      await open('STAFF');
      await answer();

      expect(root.textContent).not.toContain('Schedule workshop');
    });

    it('opens the new-workshop form from the button', async () => {
      await open('MANAGER');
      await answer();

      button('Schedule workshop').click();
      await settle();

      expect(router.url).toBe('/workshops/new');
    });
  });
});
