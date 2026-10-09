import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { Role } from '@core/auth/role';
import { ProblemError, isProblemError, toProblemError } from '@core/http/problem';

export interface StaffAccount {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface StaffAccountPage {
  items: StaffAccount[];
  page: number;
  size: number;
  totalItems: number;
}

export interface StaffAccountQuery {
  role: Role | null;
  /** `null` means "both active and deactivated". */
  active: boolean | null;
  /** Zero-based, like the API. */
  page: number;
  size: number;
}

export interface CreateStaffAccount {
  email: string;
  fullName: string;
  role: Role;
  temporaryPassword: string;
}

export interface UpdateStaffAccount {
  fullName?: string;
  role?: Role;
  active?: boolean;
  version: number;
}

export interface RoleOption {
  value: Role;
  label: string;
  description: string;
}

export const ROLE_OPTIONS: readonly RoleOption[] = [
  {
    value: 'ADMIN',
    label: 'Admin',
    description: "Creates staff accounts and sets roles. Doesn't handle bookings.",
  },
  {
    value: 'MANAGER',
    label: 'Programme manager',
    description: 'Schedules and edits workshops, and can book attendees.',
  },
  {
    value: 'STAFF',
    label: 'Front desk',
    description: 'Registers and cancels attendees.',
  },
];

/** How a role reads on this screen (never the raw enum). */
export function roleName(role: Role): string {
  return ROLE_OPTIONS.find((o) => o.value === role)?.label ?? 'Unknown role';
}

export const PASSWORD_MIN_LENGTH = 10;
export const GENERATED_PASSWORD_LENGTH = 14;

/** No 0/O, 1/l/I: easy to read out or type from a note. */
export const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';

type RandomFill = (bytes: Uint8Array) => Uint8Array;

/** A random temporary password from `PASSWORD_ALPHABET`, drawn without modulo bias. */
export function generateTemporaryPassword(
  length = GENERATED_PASSWORD_LENGTH,
  fill: RandomFill = (bytes) => crypto.getRandomValues(bytes),
): string {
  const limit = 256 - (256 % PASSWORD_ALPHABET.length);
  let result = '';
  while (result.length < length) {
    const bytes = fill(new Uint8Array(length * 2));
    for (const byte of bytes) {
      if (byte < limit && result.length < length) {
        result += PASSWORD_ALPHABET[byte % PASSWORD_ALPHABET.length];
      }
    }
  }
  return result;
}

/** Mirrors the server: at least 10 characters and not containing the account's email. */
export function temporaryPasswordValidator(email: () => string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = typeof control.value === 'string' ? control.value : '';
    if (value === '') {
      return null; // `required` reports an empty value
    }
    if (value.length < PASSWORD_MIN_LENGTH) {
      return { minlength: true };
    }
    const address = email().trim().toLowerCase();
    if (address !== '' && value.toLowerCase().includes(address)) {
      return { containsEmail: true };
    }
    return null;
  };
}

/** Rejects names that are only spaces. */
export const notBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value !== '' && control.value.trim() === ''
    ? { required: true }
    : null;

export function passwordErrorMessage(errors: ValidationErrors | null): string | null {
  if (errors === null) {
    return null;
  }
  if (errors['required']) {
    return 'Enter a temporary password, or press Generate.';
  }
  if (errors['minlength']) {
    return `Use at least ${PASSWORD_MIN_LENGTH} characters.`;
  }
  if (errors['containsEmail']) {
    return "The password can't contain the email address.";
  }
  if (typeof errors['server'] === 'string') {
    return errors['server'];
  }
  return null;
}

/** A short, locale-formatted date for list rows and details. */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date);
}

export function firstName(fullName: string): string {
  const first = fullName.trim().split(/\s+/)[0];
  return first === undefined || first === '' ? 'This person' : first;
}

/** The failure as a ProblemError (the HTTP interceptor already does this; raw responses are tolerated). */
export function asProblem(error: unknown): ProblemError | null {
  if (isProblemError(error)) {
    return error;
  }
  return error instanceof HttpErrorResponse ? toProblemError(error) : null;
}
