import { isTrustedRedirect, safeReturnUrl } from './safe-redirect';

describe('isTrustedRedirect', () => {
  const hosts = ['rzp.io', 'razorpay.com'];
  const origin = 'https://www.baldevairways.example';

  it('allows trusted payment hosts and their subdomains over HTTPS', () => {
    expect(isTrustedRedirect('https://rzp.io/l/abc123', hosts, origin)).toBe(true);
    expect(isTrustedRedirect('https://pages.razorpay.com/pay', hosts, origin)).toBe(true);
  });

  it('allows our own origin (the offline payment stub redirects back to us)', () => {
    expect(
      isTrustedRedirect(
        `${origin}/booking-success/5?razorpay_payment_id=pay_STUBOK1`,
        hosts,
        origin,
      ),
    ).toBe(true);
  });

  it('refuses look-alike hosts', () => {
    expect(isTrustedRedirect('https://rzp.io.evil.example/pay', hosts, origin)).toBe(false);
    expect(isTrustedRedirect('https://evilrzp.io/pay', hosts, origin)).toBe(false);
  });

  it('refuses plain HTTP and other schemes', () => {
    expect(isTrustedRedirect('http://rzp.io/l/abc', hosts, origin)).toBe(false);
    expect(isTrustedRedirect('javascript:alert(1)', hosts, origin)).toBe(false);
  });
});

describe('safeReturnUrl', () => {
  it('keeps paths inside the app', () => {
    expect(safeReturnUrl('/book/42?cabin=ECONOMY')).toBe('/book/42?cabin=ECONOMY');
  });

  it('falls back to home for anything that could leave the site', () => {
    expect(safeReturnUrl('https://evil.example')).toBe('/');
    expect(safeReturnUrl('//evil.example')).toBe('/');
    expect(safeReturnUrl('/\\evil.example')).toBe('/');
    expect(safeReturnUrl(undefined)).toBe('/');
  });
});
