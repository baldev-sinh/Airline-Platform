import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { ApiError, toApiError } from '../../core/http/api-error';
import { safeReturnUrl } from '../../core/util/safe-redirect';
import { FieldError } from '../../shared/field-error';
import {
  PASSWORD_MAX,
  PASSWORD_MIN,
  PHONE_HINT,
  normalizePhone,
  phoneNumber,
} from '../../shared/validators';

@Component({
  selector: 'app-signup-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError],
  templateUrl: './signup-page.html',
  styleUrl: './auth-pages.scss',
})
export class SignupPage {
  readonly returnUrl = input<string>();

  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly passwordMin = PASSWORD_MIN;
  protected readonly phoneHint = PHONE_HINT;

  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', [phoneNumber]],
    password: [
      '',
      [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)],
    ],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly showPassword = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const value = this.form.getRawValue();
    const phone = normalizePhone(value.phone);
    this.auth
      .signup({
        fullName: value.fullName.trim(),
        email: value.email.trim(),
        password: value.password,
        ...(phone ? { phone } : {}), // optional: only sent when filled in
      })
      .subscribe({
        next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
        error: (err: unknown) => {
          const apiError = toApiError(err);
          this.showServerFieldErrors(apiError);
          this.error.set(apiError);
          this.submitting.set(false);
        },
      });
  }

  /** The server may reject a specific field ("email already registered"): show it on that field. */
  private showServerFieldErrors(apiError: ApiError): void {
    if (apiError.code === 'EMAIL_TAKEN') {
      this.form.controls.email.setErrors({
        server: { message: 'This email already has an account. Try signing in.' },
      });
    }
    for (const fieldError of apiError.fieldErrors) {
      const control = this.form.get(fieldError.field);
      control?.setErrors({ server: { message: fieldError.message } });
      control?.markAsTouched();
    }
  }
}
