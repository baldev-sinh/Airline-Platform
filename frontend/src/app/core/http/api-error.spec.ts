import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, toApiError } from './api-error';

describe('toApiError', () => {
  it('keeps the server message and code for business errors', () => {
    const error = toApiError(
      new HttpErrorResponse({
        status: 409,
        error: { status: 409, detail: 'Seat 12A is no longer available', code: 'SEAT_UNAVAILABLE' },
      }),
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('SEAT_UNAVAILABLE');
    expect(error.message).toBe('Seat 12A is no longer available');
  });

  it('collects field errors from validation failures', () => {
    const error = toApiError(
      new HttpErrorResponse({
        status: 400,
        error: {
          code: 'VALIDATION_FAILED',
          detail: 'Validation failed',
          errors: [{ field: 'email', message: 'Email should be valid' }],
        },
      }),
    );
    expect(error.fieldErrors).toEqual([{ field: 'email', message: 'Email should be valid' }]);
  });

  it('never shows server text for 5xx errors', () => {
    const error = toApiError(
      new HttpErrorResponse({ status: 500, error: { detail: 'NullPointerException at line 42' } }),
    );
    expect(error.message).not.toContain('NullPointer');
    expect(error.message).toContain('on our side');
  });

  it('explains network failures', () => {
    const error = toApiError(new HttpErrorResponse({ status: 0 }));
    expect(error.isNetworkError).toBe(true);
    expect(error.code).toBe('NETWORK_ERROR');
  });

  it('returns an existing ApiError unchanged', () => {
    const original = new ApiError(404, 'NOT_FOUND', 'Gone');
    expect(toApiError(original)).toBe(original);
  });
});
