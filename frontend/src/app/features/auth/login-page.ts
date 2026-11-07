import { Component, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { ApiError, toApiError } from '../../core/http/api-error';
import { safeReturnUrl } from '../../core/util/safe-redirect';
import { FieldError } from '../../shared/field-error';
import { PASSWORD_MAX } from '../../shared/validators';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError],
  templateUrl: './login-page.html',
  styleUrl: './auth-pages.scss',
})
export class LoginPage {
  /** Where to go after signing in, e.g. the checkout the guest was trying to open. */
  readonly returnUrl = input<string>();
  /** "expired" when the session ran out. */
  readonly reason = input<string>();

  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.maxLength(PASSWORD_MAX)]],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly sessionExpired = computed(
    () => this.reason() === 'expired' || this.auth.sessionExpired(),
  );

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const { email, password } = this.form.getRawValue();
    this.auth.login({ email: email.trim(), password }).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (err: unknown) => {
        const apiError = toApiError(err);
        // Never say WHICH part was wrong: that would tell attackers which emails have accounts.
        this.error.set(
          apiError.status === 401 || apiError.status === 400
            ? new ApiError(
                apiError.status,
                apiError.code,
                'That email and password combination is not right.',
              )
            : apiError,
        );
        this.submitting.set(false);
      },
    });
  }
}
