import { HttpErrorResponse } from '@angular/common/http';

import {
  GENERIC_MESSAGE,
  NETWORK_MESSAGE,
  PROBLEM_MESSAGES,
  SERVER_MESSAGE,
  messageFor,
} from './messages';
import { PROBLEM_CODES, ProblemError, toProblemError } from './problem';

describe('messageFor', () => {
  it.each(PROBLEM_CODES)('has a plain-language message for %s', (code) => {
    const message = messageFor(new ProblemError(409, code, 'raw detail from the server'));

    expect(message).toBe(PROBLEM_MESSAGES[code]);
    expect(message.length).toBeGreaterThan(20);
    // Never leak codes, status numbers or the server's detail text.
    expect(message).not.toMatch(/[A-Z]+_[A-Z_]+/);
    expect(message).not.toMatch(/\b\d{3}\b/);
    expect(message).not.toContain('raw detail');
  });

  it('uses the agreed wording for the key conflicts', () => {
    expect(messageFor(new ProblemError(409, 'WORKSHOP_FULL', null))).toBe(
      'Sorry — the last seat was just taken by a colleague.',
    );
    expect(messageFor(new ProblemError(409, 'STALE_VERSION', null))).toBe(
      'Someone else changed this while you were editing. Reload to see their changes.',
    );
    expect(messageFor(new ProblemError(503, 'IDENTITY_UNAVAILABLE', null))).toBe(
      'Sign-in service is busy. Please try again in a moment.',
    );
  });

  it('covers network and unrecognised failures', () => {
    expect(messageFor(new ProblemError(0, 'NETWORK', null))).toBe(NETWORK_MESSAGE);
    expect(messageFor(new ProblemError(502, 'UNKNOWN', null))).toBe(SERVER_MESSAGE);
    expect(messageFor(new ProblemError(418, 'UNKNOWN', null))).toBe(GENERIC_MESSAGE);
  });
});

describe('toProblemError', () => {
  it('reads problem+json bodies, including field errors', () => {
    const problem = toProblemError(
      new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          title: 'Bad request',
          detail: 'Validation failed',
          code: 'VALIDATION_FAILED',
          errors: [{ field: 'attendeeEmail', message: 'must be an email' }, { nope: true }],
        },
      }),
    );

    expect(problem.status).toBe(400);
    expect(problem.code).toBe('VALIDATION_FAILED');
    expect(problem.detail).toBe('Validation failed');
    expect(problem.fieldErrors).toEqual([{ field: 'attendeeEmail', message: 'must be an email' }]);
    expect(problem.fieldMessage('attendeeEmail')).toBe('must be an email');
    expect(problem.fieldMessage('other')).toBeNull();
  });

  it('treats a status of 0 as a network error', () => {
    const problem = toProblemError(new HttpErrorResponse({ status: 0 }));

    expect(problem.code).toBe('NETWORK');
    expect(problem.isNetworkError).toBe(true);
  });

  it('tolerates bodies that are not problem+json', () => {
    const html = toProblemError(new HttpErrorResponse({ status: 502, error: '<html>' }));
    const unknownCode = toProblemError(
      new HttpErrorResponse({ status: 409, error: { code: 'SOMETHING_NEW' } }),
    );

    expect(html.code).toBe('UNKNOWN');
    expect(html.isServerError).toBe(true);
    expect(unknownCode.code).toBe('UNKNOWN');
  });
});
