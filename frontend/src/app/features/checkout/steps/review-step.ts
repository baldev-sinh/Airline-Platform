import { DatePipe } from '@angular/common';
import { Component, computed, inject, output, signal } from '@angular/core';
import { TRUSTED_PAYMENT_HOSTS } from '../../../core/config/app-config';
import { ApiError, toApiError } from '../../../core/http/api-error';
import { isTrustedRedirect } from '../../../core/util/safe-redirect';
import { GENDER_LABELS } from '../../../shared/labels';
import { MoneyPipe } from '../../../shared/money-pipe';
import { CheckoutStore, StepId } from '../checkout-store';

/** Server error codes that mean "the seats you picked are gone": send the customer back to choose again. */
const SEAT_ERRORS = new Set(['SEAT_UNAVAILABLE', 'SEAT_HOLD_LOST', 'DUPLICATE_SEAT']);

@Component({
  selector: 'app-review-step',
  imports: [DatePipe, MoneyPipe],
  templateUrl: './review-step.html',
  styleUrl: './review-step.scss',
})
export class ReviewStep {
  protected readonly store = inject(CheckoutStore);
  private readonly trustedHosts = inject(TRUSTED_PAYMENT_HOSTS);

  /** Tells the page to go back to seat selection. */
  readonly seatsLost = output<void>();
  /** "Edit" links next to each section. */
  readonly editStep = output<StepId>();

  protected readonly submitting = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly genders = GENDER_LABELS;

  protected readonly travellers = computed(() =>
    this.store.passengerForms.map((form, i) => ({
      ...form.getRawValue(),
      seat: this.store.selectedSeats()[i],
      meal: this.store.selectedMeals()[i],
    })),
  );
  protected readonly contact = this.store.travellerForm.controls.contact.getRawValue();

  protected pay(): void {
    if (!this.store.termsAccepted()) {
      this.error.set(
        new ApiError(0, 'TERMS', 'Please accept the fare rules and conditions to continue.'),
      );
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    this.store.createBooking().subscribe({
      next: (response) => {
        const url = response.checkoutUrl;
        if (!url || !isTrustedRedirect(url, this.trustedHosts, window.location.origin)) {
          // Never send a customer to a payment page we don't recognise.
          this.error.set(
            new ApiError(
              0,
              'UNTRUSTED_CHECKOUT',
              'We could not open the secure payment page. Your seats are held: you can pay from My trips.',
            ),
          );
          this.submitting.set(false);
          return;
        }
        // Leave the app for the payment provider. They send the customer back to /booking-success.
        window.location.assign(url);
      },
      error: (err: unknown) => {
        const apiError = toApiError(err);
        this.submitting.set(false);
        if (SEAT_ERRORS.has(apiError.code)) {
          this.store.seatNotice.set(`${apiError.message} Please choose another seat.`);
          this.store.refreshSeats().subscribe({ complete: () => this.seatsLost.emit() });
          return;
        }
        this.error.set(apiError);
      },
    });
  }
}
