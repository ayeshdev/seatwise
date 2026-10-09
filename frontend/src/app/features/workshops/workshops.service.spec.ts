import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API, makeSummary, makeWorkshop, provideApiTesting } from './workshop.fixtures';
import { WorkshopRequest } from './workshop.models';
import { WorkshopsService } from './workshops.service';

describe('WorkshopsService', () => {
  let service: WorkshopsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideApiTesting('MANAGER').providers });
    service = TestBed.inject(WorkshopsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('builds the search URL with repeated status parameters', () => {
    service
      .search({
        from: '2030-10-14',
        to: '2030-10-20',
        statuses: ['OPEN', 'FULL'],
        locationId: 'loc-1',
        hasSeats: true,
        q: ' pottery ',
        page: 2,
        size: 20,
        sort: 'startsAt,asc',
      })
      .subscribe();

    const req = http.expectOne((r) => r.url === `${API}/workshops`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2030-10-14');
    expect(req.request.params.get('to')).toBe('2030-10-20');
    expect(req.request.params.getAll('status')).toEqual(['OPEN', 'FULL']);
    expect(req.request.params.get('locationId')).toBe('loc-1');
    expect(req.request.params.get('hasSeats')).toBe('true');
    expect(req.request.params.get('q')).toBe('pottery');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('startsAt,asc');
    expect(req.request.urlWithParams).toContain('status=OPEN&status=FULL');
    req.flush({ items: [], page: 2, size: 20, totalItems: 0, searchMode: 'index' });
  });

  it('leaves out filters that are not set', () => {
    service.search({ hasSeats: false, q: '  ', statuses: [], locationId: null }).subscribe();

    const req = http.expectOne((r) => r.url === `${API}/workshops`);
    expect(req.request.params.keys()).toEqual([]);
    req.flush({ items: [], page: 0, size: 20, totalItems: 0, searchMode: 'index' });
  });

  it('returns the search result as sent', () => {
    let items = 0;
    let mode = '';
    service.search({}).subscribe((r) => {
      items = r.items.length;
      mode = r.searchMode;
    });

    http
      .expectOne((r) => r.url === `${API}/workshops`)
      .flush({ items: [makeSummary()], page: 0, size: 20, totalItems: 1, searchMode: 'fallback' });

    expect(items).toBe(1);
    expect(mode).toBe('fallback');
  });

  it('gets one workshop', () => {
    service.get('w-1').subscribe();

    const req = http.expectOne(`${API}/workshops/w-1`);
    expect(req.request.method).toBe('GET');
    req.flush(makeWorkshop());
  });

  it('creates a workshop', () => {
    const body: WorkshopRequest = {
      code: 'POT-1',
      title: 'T',
      instructor: 'I',
      locationId: 'loc-1',
      startsAt: '2030-10-17T09:30:00Z',
      endsAt: '2030-10-17T12:00:00Z',
      capacity: 10,
    };
    service.create(body).subscribe();

    const req = http.expectOne(`${API}/workshops`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(makeWorkshop());
  });

  it('updates a workshop and sends the version it was editing', () => {
    service
      .update('w-1', {
        code: 'POT-1',
        title: 'T',
        instructor: 'I',
        locationId: 'loc-1',
        startsAt: '2030-10-17T09:30:00Z',
        endsAt: '2030-10-17T12:00:00Z',
        capacity: 10,
        version: 4,
      })
      .subscribe();

    const req = http.expectOne(`${API}/workshops/w-1`);
    expect(req.request.method).toBe('PUT');
    expect((req.request.body as WorkshopRequest).version).toBe(4);
    req.flush(makeWorkshop());
  });

  it('cancels a workshop with an optional reason', () => {
    service.cancel('w-1', 'Instructor unwell').subscribe();

    const req = http.expectOne(`${API}/workshops/w-1/cancel`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Instructor unwell' });
    req.flush(makeWorkshop({ status: 'CANCELLED' }));
  });

  it('lists locations', () => {
    service.listLocations().subscribe();

    const req = http.expectOne(`${API}/locations`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'loc-1', name: 'Riverside' }]);
  });
});
