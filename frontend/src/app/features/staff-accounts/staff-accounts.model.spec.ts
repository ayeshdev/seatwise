import { FormControl } from '@angular/forms';

import {
  GENERATED_PASSWORD_LENGTH,
  PASSWORD_ALPHABET,
  firstName,
  generateTemporaryPassword,
  roleName,
  temporaryPasswordValidator,
} from './staff-accounts.model';

describe('generateTemporaryPassword', () => {
  it('makes 14 characters from the readable alphabet', () => {
    for (let i = 0; i < 50; i++) {
      const password = generateTemporaryPassword();
      expect(password).toHaveLength(GENERATED_PASSWORD_LENGTH);
      expect(GENERATED_PASSWORD_LENGTH).toBe(14);
      for (const char of password) {
        expect(PASSWORD_ALPHABET).toContain(char);
      }
    }
  });

  it('leaves out look-alike characters', () => {
    expect(PASSWORD_ALPHABET).not.toMatch(/[0O1lI]/);
  });

  it('is different each time', () => {
    const passwords = new Set(Array.from({ length: 20 }, () => generateTemporaryPassword()));
    expect(passwords.size).toBe(20);
  });

  it('skips random bytes that would bias the choice', () => {
    // 54 characters: bytes 216..255 are rejected, so 255 and 216 are skipped, 0 -> first character.
    const fill = jest
      .fn<Uint8Array, [Uint8Array]>()
      .mockImplementation((bytes) => bytes.fill(0).fill(255, 0, 4));
    const password = generateTemporaryPassword(3, fill);
    expect(password).toBe(PASSWORD_ALPHABET[0].repeat(3));
  });
});

describe('temporaryPasswordValidator', () => {
  const validate = (value: string, email: string) =>
    temporaryPasswordValidator(() => email)(new FormControl(value));

  it('leaves empty values to `required`', () => {
    expect(validate('', 'a@b.co')).toBeNull();
  });

  it('wants at least 10 characters', () => {
    expect(validate('Short123', 'a@b.co')).toEqual({ minlength: true });
    expect(validate('Longer1234', 'a@b.co')).toBeNull();
  });

  it('refuses a password that contains the email, ignoring case', () => {
    expect(validate('xxSAM@seatwise.localxx', 'sam@seatwise.local')).toEqual({
      containsEmail: true,
    });
  });
});

describe('labels', () => {
  it('names roles in plain words', () => {
    expect(roleName('ADMIN')).toBe('Admin');
    expect(roleName('MANAGER')).toBe('Programme manager');
    expect(roleName('STAFF')).toBe('Front desk');
  });

  it('uses the first name in sentences', () => {
    expect(firstName('Sam Patel')).toBe('Sam');
    expect(firstName('  ')).toBe('This person');
  });
});
