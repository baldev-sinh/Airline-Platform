/**
 * Reads the expiry time from a JWT (JSON Web Token).
 *
 * A JWT has three parts separated by dots: header.payload.signature. The payload is Base64URL
 * encoded JSON, for example { "sub": "42", "email": "...", "roles": ["ROLE_USER"], "exp": 1762500000 }.
 *
 * IMPORTANT: the browser does NOT verify the signature. It cannot, because the secret lives on
 * the server. We only read `exp` to know when to sign the user out. The API gateway verifies
 * every token on every request, so a tampered token gets nothing.
 */
export function readTokenExpiry(token: string): number | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const payload = JSON.parse(atob(padded)) as { exp?: unknown };
    // `exp` is in SECONDS since 1970. JavaScript dates use MILLISECONDS.
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}
