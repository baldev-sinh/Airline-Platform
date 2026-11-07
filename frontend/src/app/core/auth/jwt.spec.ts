import { readTokenExpiry } from './jwt';

/** Builds an unsigned token with the given payload. Fine for tests: the browser never checks signatures. */
function fakeToken(payload: object): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`;
}

describe('readTokenExpiry', () => {
  it('converts exp (seconds) to milliseconds', () => {
    expect(readTokenExpiry(fakeToken({ sub: '1', exp: 1_800_000_000 }))).toBe(1_800_000_000_000);
  });

  it('returns null when there is no exp claim', () => {
    expect(readTokenExpiry(fakeToken({ sub: '1' }))).toBeNull();
  });

  it('returns null for malformed tokens instead of throwing', () => {
    expect(readTokenExpiry('not-a-jwt')).toBeNull();
    expect(readTokenExpiry('a.%%%.c')).toBeNull();
  });
});
