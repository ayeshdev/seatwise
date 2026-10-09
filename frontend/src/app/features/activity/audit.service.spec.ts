import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApiTesting } from '@features/workshops/workshop.fixtures';

import { AUDIT_URL, makeEvent, makePage } from './audit.fixtures';
import { AuditService } from './audit.service';

describe('AuditService', () => {
  let service: AuditService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideApiTesting('MANAGER').providers });
    service = TestBed.inject(AuditService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a page and leaves out every filter that is not set', () => {
    service.list({ page: 0, size: 20 }).subscribe();

    const req = http.expectOne((r) => r.url === AUDIT_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(makePage([]));
  });

  it('sends the entity, workshop and date filters it is given', () => {
    service
      .list({
        entityType: 'REGISTRATION',
        entityId: 'r-1',
        workshopId: 'w-1',
        from: '2030-10-01',
        to: '2030-10-31',
        page: 2,
        size: 10,
      })
      .subscribe();

    const req = http.expectOne((r) => r.url === AUDIT_URL);
    const p = req.request.params;
    expect(p.get('entityType')).toBe('REGISTRATION');
    expect(p.get('entityId')).toBe('r-1');
    expect(p.get('workshopId')).toBe('w-1');
    expect(p.get('from')).toBe('2030-10-01');
    expect(p.get('to')).toBe('2030-10-31');
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('10');
    req.flush(makePage([]));
  });

  it('treats null and empty filters as not set', () => {
    service
      .list({ entityType: null, entityId: '', workshopId: null, from: null, to: '', page: 0, size: 5 })
      .subscribe();

    const req = http.expectOne((r) => r.url === AUDIT_URL);
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    req.flush(makePage([]));
  });

  it('hands back the page as the API sent it', () => {
    let received: unknown;
    service.list({ page: 0, size: 20 }).subscribe((page) => (received = page));

    const page = makePage([makeEvent()], { totalItems: 41 });
    http.expectOne((r) => r.url === AUDIT_URL).flush(page);
    expect(received).toEqual(page);
  });
});
