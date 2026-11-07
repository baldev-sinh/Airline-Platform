/** The settings every environment file must provide. TypeScript errors if one is missing. */
export interface AppEnvironment {
  readonly production: boolean;
  /** Prefix for API calls. '' means "same origin as the web app". */
  readonly apiBaseUrl: string;
  /** Hostnames we allow as payment checkout redirects (exact match or subdomain). */
  readonly trustedPaymentHosts: readonly string[];
}
