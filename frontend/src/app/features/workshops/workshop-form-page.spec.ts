import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { ToastService } from '@core/layout/toast.service';

import { WorkshopFormPage, scheduleValidator } from './workshop-form-page';
import { toIsoInstant } from './workshop-format';
import {
  API,
  PROBLEM_HEADERS,
  makeWorkshop,
  problem,
  provideApiTesting,
} from './workshop.fixtures';
import { WorkshopRequest } from './workshop.models';

@Component({ selector: 'sw-stub', template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class Stub {}

const LOCATIONS = [
  { id: 'loc-1', name: 'Riverside' },
  { id: 'loc-2', name: 'Hilltop' },
];

describe('scheduleValidator', () => {
  const now = () => new Date(2026, 9, 9, 12, 0);

  function group(date: string, start: string, end: string): FormGroup {
    return new FormGroup({
      date: new FormControl(date),
      startTime: new FormControl(start),
      endTime: new FormControl(end),
    });
  }

  it('accepts a future start with the end after it', () => {
    expect(scheduleValidator(() => true, now)(group('2026-10-10', '09:00', '11:00'))).toBeNull();
  });

  it('flags an end that is not after the start', () => {
    const errors = scheduleValidator(() => true, now)(group('2026-10-10', '11:00', '11:00'));

    expect(errors).toEqual({ endBeforeStart: true });
  });

  it('flags a start in the past only when asked to', () => {
    expect(scheduleValidator(() => true, now)(group('2026-10-09', '09:00', '11:00'))).toEqual({
      startInPast: true,
    });
    expect(scheduleValidator(() => false, now)(group('2026-10-09', '09:00', '11:00'))).toBeNull();
  });

  it('waits until all three fields are filled', () => {
    expect(scheduleValidator(() => true, now)(group('', '09:00', ''))).toBeNull();
  });
});

describe('WorkshopFormPage', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let toasts: ToastService;
  let root: HTMLElement;

  const isLocations = (r: HttpRequest<unknown>) => r.url === `${API}/locations`;

  async function open(url: string): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        ...provideApiTesting('MANAGER').providers,
        provideRouter([
          { path: 'workshops/new', component: WorkshopFormPage },
          { path: 'workshops/:id/edit', component: WorkshopFormPage },
          { path: 'workshops/:id', component: Stub },
          { path: 'workshops', component: Stub },
        ]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    toasts = TestBed.inject(ToastService);
    jest.spyOn(toasts, 'success');
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, WorkshopFormPage);
    root = harness.routeNativeElement as HTMLElement;
    harness.detectChanges();
  }

  async function openCreate(): Promise<void> {
    await open('/workshops/new');
    http.expectOne(isLocations).flush(LOCATIONS);
    await settle();
  }

  async function openEdit(workshop = makeWorkshop()): Promise<void> {
    await open('/workshops/w-1/edit');
    http.expectOne(isLocations).flush(LOCATIONS);
    http.expectOne(`${API}/workshops/w-1`).flush(workshop);
    await settle();
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function control(name: string): HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement {
    return root.querySelector(`[formcontrolname="${name}"]`) as HTMLInputElement;
  }

  function set(name: string, value: string): void {
    const el = control(name);
    el.value = value;
    el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input'));
    el.dispatchEvent(new Event('blur'));
    harness.detectChanges();
  }

  function fillValid(): void {
    set('code', 'POT-0413');
    set('title', 'Glazing basics');
    set('instructor', 'Amara Silva');
    set('locationId', 'loc-2');
    set('date', '2030-10-17');
    set('startTime', '09:30');
    set('endTime', '12:00');
    set('capacity', '12');
    set('description', '  Bring an apron.  ');
  }

  async function save(): Promise<void> {
    (root.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await settle();
  }

  function fieldText(name: string): string {
    return control(name).closest('sw-form-field')?.textContent ?? '';
  }

  function button(label: string): HTMLButtonElement {
    const found = Array.from(root.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    );
    if (!found) {
      throw new Error(`No button ${label}`);
    }
    return found;
  }

  afterEach(() => http.verify());

  describe('scheduling a new workshop', () => {
    it('offers the three locations', async () => {
      await openCreate();

      const options = Array.from(control('locationId').querySelectorAll('option')).map(
        (o) => o.textContent?.trim(),
      );
      expect(options).toEqual(['Choose a location', 'Riverside', 'Hilltop']);
    });

    it('asks for what is missing instead of sending', async () => {
      await openCreate();

      await save();

      http.expectNone(`${API}/workshops`);
      expect(fieldText('code')).toContain('Enter a workshop code.');
      expect(fieldText('title')).toContain('Enter a title.');
      expect(fieldText('instructor')).toContain("Enter the instructor's name.");
      expect(fieldText('locationId')).toContain('Choose where it is held.');
      expect(fieldText('date')).toContain('Pick a date.');
      expect(fieldText('startTime')).toContain('Pick a start time.');
      expect(fieldText('endTime')).toContain('Pick an end time.');
      expect(fieldText('capacity')).toContain('Enter the number of seats.');
    });

    it('types the code in capitals and checks its shape', async () => {
      await openCreate();

      set('code', 'pot-0412');
      expect((control('code') as HTMLInputElement).value).toBe('POT-0412');
      expect(fieldText('code')).not.toContain('Use 3 to 32');

      set('code', 'a b');
      expect(fieldText('code')).toContain('Use 3 to 32 capital letters, numbers or dashes');
    });

    it.each([['0'], ['501'], ['2.5']])('refuses %s seats', async (seats) => {
      await openCreate();

      set('capacity', seats);

      expect(fieldText('capacity')).toContain('Enter a whole number from 1 to 500.');
    });

    it('checks that the end is after the start', async () => {
      await openCreate();

      set('date', '2030-10-17');
      set('startTime', '12:00');
      set('endTime', '09:00');

      expect(fieldText('endTime')).toContain('The end time must be after the start time.');
    });

    it('checks that a new workshop starts in the future', async () => {
      await openCreate();

      set('date', '2020-01-01');
      set('startTime', '09:00');
      set('endTime', '10:00');

      expect(fieldText('startTime')).toContain('The start must be in the future.');
    });

    it('sends the local date and times as instants, without a version', async () => {
      await openCreate();
      fillValid();

      await save();

      const req = http.expectOne(`${API}/workshops`);
      expect(req.request.method).toBe('POST');
      const body = req.request.body as WorkshopRequest;
      expect(body).toEqual({
        code: 'POT-0413',
        title: 'Glazing basics',
        instructor: 'Amara Silva',
        description: 'Bring an apron.',
        locationId: 'loc-2',
        startsAt: toIsoInstant('2030-10-17', '09:30'),
        endsAt: toIsoInstant('2030-10-17', '12:00'),
        capacity: 12,
      });
      expect('version' in body).toBe(false);
      req.flush(makeWorkshop({ id: 'w-9' }), { status: 201, statusText: 'Created' });
      await settle();

      expect(toasts.success).toHaveBeenCalledWith('The workshop is scheduled.');
      expect(router.url).toBe('/workshops/w-9');
    });

    it('sends no description when it is left empty', async () => {
      await openCreate();
      fillValid();
      set('description', '   ');

      await save();

      const req = http.expectOne(`${API}/workshops`);
      expect((req.request.body as WorkshopRequest).description).toBeNull();
      req.flush(makeWorkshop(), { status: 201, statusText: 'Created' });
    });

    it('shows the server complaints next to the fields they are about', async () => {
      await openCreate();
      fillValid();

      await save();
      http.expectOne(`${API}/workshops`).flush(
        problem('VALIDATION_FAILED', 'Invalid request', [
          { field: 'code', message: 'That code is already used.' },
          { field: 'startsAt', message: 'The start must be in the future.' },
        ]),
        { status: 400, statusText: 'Bad Request' },
      );
      await settle();

      expect(fieldText('code')).toContain('That code is already used.');
      expect(fieldText('startTime')).toContain('The start must be in the future.');
      expect(router.url).toBe('/workshops/new');

      // Editing the field clears the old complaint.
      set('code', 'POT-0414');
      expect(fieldText('code')).not.toContain('already used');
    });
  });

  describe('editing a workshop', () => {
    it('fills the form from the workshop', async () => {
      await openEdit();

      expect((control('code') as HTMLInputElement).value).toBe('POT-0412');
      expect((control('title') as HTMLInputElement).value).toBe('Wheel-throwing for beginners');
      expect((control('locationId') as HTMLSelectElement).value).toBe('loc-1');
      expect((control('date') as HTMLInputElement).value).toBe('2030-10-17');
      expect((control('startTime') as HTMLInputElement).value).toBe('09:30');
      expect((control('endTime') as HTMLInputElement).value).toBe('12:00');
      expect((control('capacity') as HTMLInputElement).value).toBe('20');
      expect(root.querySelector('h1')?.textContent).toContain('Edit workshop');
    });

    it('sends the version it loaded with the PUT', async () => {
      await openEdit(makeWorkshop({ version: 7 }));
      set('title', 'Wheel-throwing, second term');

      await save();

      const req = http.expectOne(`${API}/workshops/w-1`);
      expect(req.request.method).toBe('PUT');
      const body = req.request.body as WorkshopRequest;
      expect(body.version).toBe(7);
      expect(body.title).toBe('Wheel-throwing, second term');
      req.flush(makeWorkshop({ version: 8 }));
      await settle();

      expect(toasts.success).toHaveBeenCalledWith('Changes saved.');
      expect(router.url).toBe('/workshops/w-1');
    });

    it('does not insist on a future start when editing', async () => {
      const started = makeWorkshop({
        startsAt: new Date(2020, 0, 1, 9, 0).toISOString(),
        endsAt: new Date(2020, 0, 1, 10, 0).toISOString(),
      });
      await openEdit(started);

      await save();

      http.expectOne(`${API}/workshops/w-1`).flush(started);
    });

    it('says how many seats are taken when capacity is set too low', async () => {
      await openEdit(makeWorkshop({ seatsTaken: 17 }));
      set('capacity', '10');

      await save();
      http
        .expectOne(`${API}/workshops/w-1`)
        .flush(problem('CAPACITY_BELOW_TAKEN', 'Capacity 10 is below the 17 seats already booked'), PROBLEM_HEADERS);
      await settle();

      expect(fieldText('capacity')).toContain(
        'Capacity 10 is below the 17 seats already booked — at least 17 seats are already taken.',
      );
      expect(root.textContent).not.toContain('CAPACITY_BELOW_TAKEN');
    });

    it('still says how many seats are taken when the server gives no detail', async () => {
      await openEdit(makeWorkshop({ seatsTaken: 17 }));

      await save();
      http
        .expectOne(`${API}/workshops/w-1`)
        .flush({ code: 'CAPACITY_BELOW_TAKEN' }, PROBLEM_HEADERS);
      await settle();

      expect(fieldText('capacity')).toContain('At least 17 seats are already taken.');
    });

    it('shows a banner when someone else saved first, and reloads on request', async () => {
      await openEdit(makeWorkshop({ version: 4, title: 'Old title' }));
      set('title', 'My title');

      await save();
      http.expectOne(`${API}/workshops/w-1`).flush(problem('STALE_VERSION'), PROBLEM_HEADERS);
      await settle();

      expect(root.textContent).toContain('Someone else changed this while you were editing.');
      expect(root.textContent).not.toContain('STALE_VERSION');

      button('Reload').click();
      await settle();
      http.expectOne(`${API}/workshops/w-1`).flush(makeWorkshop({ version: 5, title: 'Their title' }));
      await settle();

      expect(root.textContent).not.toContain('Someone else changed this');
      expect((control('title') as HTMLInputElement).value).toBe('Their title');

      await save();
      const retry = http.expectOne(`${API}/workshops/w-1`);
      expect((retry.request.body as WorkshopRequest).version).toBe(5);
      retry.flush(makeWorkshop({ version: 6 }));
    });

    it('says so when the workshop cannot be opened', async () => {
      await open('/workshops/w-1/edit');
      http.expectOne(isLocations).flush(LOCATIONS);
      http
        .expectOne(`${API}/workshops/w-1`)
        .flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
      await settle();

      expect(root.textContent).toContain("We couldn't open this workshop");
      expect(root.querySelector('form')).toBeNull();
    });
  });
});
