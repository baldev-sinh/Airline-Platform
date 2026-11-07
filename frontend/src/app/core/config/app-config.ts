import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Brand settings in one place, so the name or support details can change without hunting
 * through templates.
 */
export const BRAND = {
  name: 'Baldev Airways',
  supportEmail: 'care@baldevairways.example',
  supportPhone: '+91 22 4000 1234',
  /** Currency used when a response does not state one. The backend prices in Indian rupees. */
  defaultCurrency: 'INR',
} as const;

/**
 * An InjectionToken lets classes ask Angular for a value with `inject(API_BASE_URL)` instead of
 * importing the environment file directly. Tests can then provide a different value easily.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});

export const TRUSTED_PAYMENT_HOSTS = new InjectionToken<readonly string[]>(
  'TRUSTED_PAYMENT_HOSTS',
  {
    providedIn: 'root',
    factory: () => environment.trustedPaymentHosts,
  },
);
