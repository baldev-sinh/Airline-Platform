import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Form rules shared by several forms. They mirror the backend's rules, so the customer sees the
 * problem immediately instead of after a round trip. The server still validates everything:
 * browser checks are for convenience, never for security.
 */

/** International phone number, e.g. +919876543210 (same regex as PassengerRequest on the server). */
export const PHONE_PATTERN = /^\+[0-9]{1,3}[0-9]{4,14}$/;
export const PHONE_HINT = 'Include the country code, e.g. +919876543210';

/** The backend requires 10 to 72 characters (72 is the bcrypt limit). */
export const PASSWORD_MIN = 10;
export const PASSWORD_MAX = 72;

/** Today as "YYYY-MM-DD" in the user's own time zone (not UTC, which can be a different day). */
export function todayIso(now = new Date()): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Adds days to a "YYYY-MM-DD" date and returns the same format. */
export function addDaysIso(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return todayIso(new Date(y, m - 1, d + days));
}

/** Date of birth must be a real date in the past, and not absurdly long ago. */
export const pastDate: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string | null;
  if (!value) {
    return null; // "required" reports empty values
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value >= todayIso()) {
    return { pastDate: { message: 'Enter a date of birth in the past' } };
  }
  if (value < '1900-01-01') {
    return { pastDate: { message: 'Please check the year' } };
  }
  return null;
};

/** A date that is today or later (departure dates). */
export const notInPast: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string | null;
  if (value && value < todayIso()) {
    return { notInPast: { message: 'Choose today or a later date' } };
  }
  return null;
};

/** Removes spaces and dashes people often type in phone numbers: "+91 98765-43210" -> "+919876543210". */
export function normalizePhone(value: string): string {
  return value.replace(/[\s()-]/g, '');
}

/** Phone in international format. Spaces and dashes are allowed while typing; we strip them on submit. */
export const phoneNumber: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string | null;
  if (!value) {
    return null;
  }
  return PHONE_PATTERN.test(normalizePhone(value)) ? null : { phone: { message: PHONE_HINT } };
};
