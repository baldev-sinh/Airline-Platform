/**
 * Build-time settings for the PRODUCTION build (`ng build`).
 *
 * Keep secrets out of this file: everything here ships to the browser and anyone can read it.
 * The development build swaps this file for `environment.development.ts` (see `fileReplacements`
 * in angular.json), so both files must export the same shape: `AppEnvironment`.
 */
import { AppEnvironment } from './app-environment';

export const environment: AppEnvironment = {
  production: true,

  // Empty string = same origin. In production a reverse proxy (see nginx/default.conf) serves the
  // app and forwards /api and /auth to the API gateway, so the browser never makes cross-origin calls.
  apiBaseUrl: '',

  // The only external sites we will ever send a customer to for payment. Anything else returned
  // by the API as a "checkoutUrl" is refused, which protects against open-redirect attacks.
  trustedPaymentHosts: ['rzp.io', 'razorpay.com', 'api.razorpay.com', 'pages.razorpay.com'],
};
