import { HttpErrorResponse } from '@angular/common/http';

/** Stable machine codes the backend puts on `application/problem+json` responses. */
export const PROBLEM_CODES = [
  'VALIDATION_FAILED',
  'UNAUTHENTICATED',
  'FORBIDDEN',
  'ACCOUNT_INACTIVE',
  'NOT_FOUND',
  'WORKSHOP_FULL',
  'WORKSHOP_NOT_OPEN',
  'DUPLICATE_REGISTRATION',
  'ALREADY_CANCELLED',
  'CAPACITY_BELOW_TAKEN',
  'STALE_VERSION',
  'EMAIL_IN_USE',
  'LAST_ADMIN',
  'SELF_MODIFICATION',
  'IDENTITY_UNAVAILABLE',
  'SERVICE_UNAVAILABLE',
] as const;

export type KnownProblemCode = (typeof PROBLEM_CODES)[number];

/** `NETWORK` = no response at all; `UNKNOWN` = a response that is not problem+json. */
export type ProblemCode = KnownProblemCode | 'NETWORK' | 'UNKNOWN';

export interface FieldError {
  field: string;
  message: string;
}

export function isKnownProblemCode(value: unknown): value is KnownProblemCode {
  return typeof value === 'string' && (PROBLEM_CODES as readonly string[]).includes(value);
}

/** A failed API call, reduced to what the UI needs. Never show `code` or `detail` to people. */
export class ProblemError extends Error {
  constructor(
    readonly status: number,
    readonly code: ProblemCode,
    readonly detail: string | null,
    readonly fieldErrors: readonly FieldError[] = [],
  ) {
    super(detail ?? `Request failed (${status})`);
    this.name = 'ProblemError';
  }

  get isNetworkError(): boolean {
    return this.code === 'NETWORK';
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }

  /** Message for one form field, if the server flagged it. */
  fieldMessage(field: string): string | null {
    return this.fieldErrors.find((e) => e.field === field)?.message ?? null;
  }
}

export function isProblemError(value: unknown): value is ProblemError {
  return value instanceof ProblemError;
}

function isFieldError(value: unknown): value is FieldError {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return typeof record['field'] === 'string' && typeof record['message'] === 'string';
}

/** Converts any HTTP failure into a `ProblemError`, tolerating non-problem bodies (proxy pages, etc.). */
export function toProblemError(error: HttpErrorResponse): ProblemError {
  if (error.status === 0) {
    return new ProblemError(0, 'NETWORK', null);
  }
  const body: unknown = error.error;
  if (typeof body === 'object' && body !== null) {
    const record = body as Record<string, unknown>;
    const code: ProblemCode = isKnownProblemCode(record['code']) ? record['code'] : 'UNKNOWN';
    const detail = typeof record['detail'] === 'string' ? record['detail'] : null;
    const errors = Array.isArray(record['errors']) ? record['errors'].filter(isFieldError) : [];
    return new ProblemError(error.status, code, detail, errors);
  }
  return new ProblemError(error.status, 'UNKNOWN', null);
}
