import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '@core/auth/auth.service';
import { SessionStore } from '@core/auth/session.store';
import { ToastService } from '@core/layout/toast.service';

import { NETWORK_MESSAGE, PROBLEM_MESSAGES } from './messages';
import { ProblemError } from './problem';
import { problemInterceptor } from './problem.interceptor';

describe('problemInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let toasts: ToastService;
  const auth = { login: jest.fn(async () => undefined), logout: jest.fn() };
  const session = { markDeactivated: jest.fn() };

  beforeEach(() => {
    auth.login.mockClear();
    session.markDeactivated.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([problemInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: SessionStore, useValue: session },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => {
    http.verify();
    for (const toast of toasts.toasts()) {
      toasts.dismiss(toast.id);
    }
  });

  async function failWith(
    body: unknown,
    init: { status: number; statusText: string },
  ): Promise<ProblemError> {
    const result = firstValueFrom(client.get('/api/v1/thing'));
    http.expectOne('/api/v1/thing').flush(body, init);
    return result.then(
      () => {
        throw new Error('expected the request to fail');
      },
      (error: unknown) => error as ProblemError,
    );
  }

  it('turns problem+json into a ProblemError and leaves 409 for the caller', async () => {
    const error = await failWith(
      { status: 409, title: 'Conflict', detail: 'No seats', code: 'WORKSHOP_FULL' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(error).toBeInstanceOf(ProblemError);
    expect(error.status).toBe(409);
    expect(error.code).toBe('WORKSHOP_FULL');
    expect(error.detail).toBe('No seats');
    expect(toasts.toasts()).toHaveLength(0);
  });

  it('keeps field errors for 400 and shows no toast', async () => {
    const error = await failWith(
      {
        status: 400,
        title: 'Bad request',
        detail: 'Validation failed',
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'attendeeName', message: 'must not be blank' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(error.fieldErrors).toEqual([{ field: 'attendeeName', message: 'must not be blank' }]);
    expect(toasts.toasts()).toHaveLength(0);
  });

  it('raises a toast for a network error', async () => {
    const result = firstValueFrom(client.get('/api/v1/thing'));
    http.expectOne('/api/v1/thing').error(new ProgressEvent('error'));
    const error = await result.catch((e: unknown) => e as ProblemError);

    expect(error).toBeInstanceOf(ProblemError);
    expect((error as ProblemError).code).toBe('NETWORK');
    expect(toasts.toasts().map((t) => t.message)).toEqual([NETWORK_MESSAGE]);
    expect(toasts.toasts()[0].tone).toBe('error');
  });

  it('raises a toast for a 5xx with a friendly message', async () => {
    await failWith(
      { status: 503, title: 'Unavailable', detail: 'idp down', code: 'IDENTITY_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(toasts.toasts().map((t) => t.message)).toEqual([PROBLEM_MESSAGES.IDENTITY_UNAVAILABLE]);
  });

  it('starts the sign-in flow on 401', async () => {
    const error = await failWith(
      { status: 401, title: 'Unauthorized', detail: 'x', code: 'UNAUTHENTICATED' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(error.code).toBe('UNAUTHENTICATED');
    expect(auth.login).toHaveBeenCalledTimes(1);
    expect(toasts.toasts()).toHaveLength(0);
  });

  it('switches to the deactivated page when the account is switched off', async () => {
    await failWith(
      { status: 403, title: 'Forbidden', detail: 'x', code: 'ACCOUNT_INACTIVE' },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(session.markDeactivated).toHaveBeenCalledTimes(1);
    expect(toasts.toasts()).toHaveLength(0);
  });
});
