/**
 * Small wrapper around localStorage / sessionStorage.
 *
 * Why not use localStorage directly? Because it can THROW: in some private-browsing modes,
 * when storage is full, or when the user has blocked site data. A crash there would break
 * sign-in for those users, so every access is wrapped in try/catch and simply degrades to
 * "nothing stored".
 */
export class BrowserStorage {
  constructor(private readonly storage: () => Storage) {}

  get(key: string): string | null {
    try {
      return this.storage().getItem(key);
    } catch {
      return null;
    }
  }

  set(key: string, value: string): void {
    try {
      this.storage().setItem(key, value);
    } catch {
      // Storage unavailable: the value just isn't persisted.
    }
  }

  remove(key: string): void {
    try {
      this.storage().removeItem(key);
    } catch {
      // Nothing to clean up.
    }
  }

  getJson<T>(key: string): T | null {
    const raw = this.get(key);
    if (raw === null) {
      return null;
    }
    try {
      return JSON.parse(raw) as T;
    } catch {
      this.remove(key); // corrupt entry: drop it
      return null;
    }
  }

  setJson(key: string, value: unknown): void {
    this.set(key, JSON.stringify(value));
  }
}

export const localStore = new BrowserStorage(() => window.localStorage);
export const sessionStore = new BrowserStorage(() => window.sessionStorage);
