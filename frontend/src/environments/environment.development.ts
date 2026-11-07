/**
 * Build-time settings for `ng serve` (development). Same shape as environment.ts.
 */
import { AppEnvironment } from './app-environment';

export const environment: AppEnvironment = {
  production: false,

  // `ng serve` proxies /api and /auth to the gateway on http://localhost:5000 (proxy.conf.json),
  // so development also behaves like same-origin production and needs no CORS setup.
  apiBaseUrl: '',

  // The offline payment stub (Spring profile `payment-stub`) redirects back to this app itself,
  // which is always allowed. Razorpay test mode uses the same hosts as production.
  trustedPaymentHosts: ['rzp.io', 'razorpay.com', 'api.razorpay.com', 'pages.razorpay.com'],
};
