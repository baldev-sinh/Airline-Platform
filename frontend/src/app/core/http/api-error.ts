import { HttpErrorResponse } from '@angular/common/http';

/** One invalid field reported by the server, e.g. { field: "passengers[0].phone", message: "..." }. */
export interface FieldError {
  readonly field: string;
  readonly message: string;
}

/**
 * Every failed API call becomes an `ApiError` (see error-interceptor.ts), so pages handle errors
 * in one predictable way instead of digging through raw HTTP responses.
 *
 * The backend sends RFC 9457 "problem details":
 *   { "status": 409, "detail": "Seat 12A is no longer available", "code": "SEAT_UNAVAILABLE" }
 * `code` is stable and safe to branch on in code. `message` is safe to show to the customer.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: readonly FieldError[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** True when the browser never got an answer (offline, DNS failure, server down). */
  get isNetworkError(): boolean {
    return this.status === 0;
  }
}

interface ProblemDetailBody {
  status?: number;
  detail?: string;
  title?: string;
  code?: string;
  errors?: { field?: string; message?: string }[];
}

/**
 * Customer-friendly fallbacks, used when the server gives no message we should show. For 5xx
 * errors we never show the server's text: it is meant for engineers, not customers.
 */
function fallbackMessage(status: number): string {
  if (status === 0) {
    return "We couldn't reach our servers. Please check your internet connection and try again.";
  }
  if (status === 401) {
    return 'Please sign in to continue.';
  }
  if (status === 403) {
    return "You don't have access to this.";
  }
  if (status === 404) {
    return "We couldn't find what you were looking for.";
  }
  if (status === 429) {
    return 'Too many attempts. Please wait a moment and try again.';
  }
  if (status >= 500) {
    return 'Something went wrong on our side. Please try again in a few minutes.';
  }
  return 'Something went wrong. Please try again.';
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (!(error instanceof HttpErrorResponse)) {
    return new ApiError(-1, 'CLIENT_ERROR', fallbackMessage(-1));
  }

  const status = error.status;
  const body: ProblemDetailBody | null =
    error.error !== null && typeof error.error === 'object'
      ? (error.error as ProblemDetailBody)
      : null;

  const code = body?.code ?? (status === 0 ? 'NETWORK_ERROR' : `HTTP_${status}`);
  const isServerFault = status >= 500 || status === 0;
  const message = !isServerFault && body?.detail ? body.detail : fallbackMessage(status);

  const fieldErrors: FieldError[] = (body?.errors ?? [])
    .filter((e) => !!e.field && !!e.message)
    .map((e) => ({ field: e.field as string, message: e.message as string }));

  return new ApiError(status, code, message, fieldErrors);
}

/** For `catchError` blocks and templates: always returns a message that is safe to display. */
export function errorMessage(error: unknown): string {
  return toApiError(error).message;
}
