import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API, makeRegistration, provideApiTesting } from '@features/workshops/workshop.fixtures';

import { RegistrationsService } from './registrations.service';

describe('RegistrationsService', () => {
  let service: RegistrationsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideApiTesting('STAFF').providers });
    service = TestBed.inject(RegistrationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the whole history, or one status', () => {
    service.history('w-1').subscribe();
    const all = http.expectOne((r) => r.url === `${API}/workshops/w-1/registrations`);
    expect(all.request.method).toBe('GET');
    expect(all.request.params.has('status')).toBe(false);
    all.flush([]);

    service.history('w-1', 'WAITLISTED').subscribe();
    const waiting = http.expectOne((r) => r.url === `${API}/workshops/w-1/registrations`);
    expect(waiting.request.params.get('status')).toBe('WAITLISTED');
    waiting.flush([]);
  });

  it('registers an attendee', () => {
    const body = {
      attendeeName: 'Priya Shah',
      attendeeEmail: 'priya@example.com',
      joinWaitlistIfFull: true,
    };
    service.register('w-1', body).subscribe();

    const req = http.expectOne(`${API}/workshops/w-1/registrations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(makeRegistration());
  });

  it('cancels a registration and returns who was promoted', () => {
    let promotedName = '';
    service.cancel('r-1', 'Changed their mind').subscribe((result) => {
      promotedName = result.promoted?.attendeeName ?? '';
    });

    const req = http.expectOne(`${API}/registrations/r-1/cancel`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Changed their mind' });
    req.flush({
      cancelled: makeRegistration({ status: 'CANCELLED' }),
      promoted: makeRegistration({ id: 'r-2', attendeeName: 'Sam Lee' }),
    });

    expect(promotedName).toBe('Sam Lee');
  });
});
