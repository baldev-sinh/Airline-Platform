/**
 * Protection against "open redirects".
 *
 * After a booking is created, the API returns a `checkoutUrl` and we send the browser there to pay.
 * If an attacker could ever influence that URL, we would be sending customers to a fake payment
 * page. So before redirecting we check that the URL is HTTPS and on a payment host we trust, or
 * on our own site (the offline payment stub used in local development redirects back to us).
 */
export function isTrustedRedirect(
  url: string,
  trustedHosts: readonly string[],
  appOrigin: string,
): boolean {
  let target: URL;
  try {
    target = new URL(url, appOrigin);
  } catch {
    return false; // not a URL at all
  }

  if (target.origin === appOrigin) {
    return true;
  }
  if (target.protocol !== 'https:') {
    return false;
  }
  const host = target.hostname.toLowerCase();
  // Exact host, or a subdomain of it. "rzp.io.evil.com" must NOT pass, hence the leading dot.
  return trustedHosts.some((trusted) => host === trusted || host.endsWith(`.${trusted}`));
}

/**
 * The sign-in page accepts `?returnUrl=/somewhere`. Only paths inside this app are allowed;
 * "https://evil.example" or "//evil.example" fall back to the home page.
 */
export function safeReturnUrl(value: string | null | undefined): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return '/';
  }
  return value;
}
