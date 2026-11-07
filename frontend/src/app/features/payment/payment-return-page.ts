import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  concat,
  defer,
  ignoreElements,
  last,
  of,
  switchMap,
  takeWhile,
  timer,
} from 'rxjs';
import { BookingApi } from '../../core/api/booking-api';
import { errorMessage } from '../../core/http/api-error';
import { Booking } from '../../core/models/api-models';
import { ErrorState, Loading } from '../../shared/async-state';
import { MoneyPipe } from '../../shared/money-pipe';

/** Same rule as the server (PaymentVerifyRequest): "pay_" followed by 6–40 letters or digits. */
const PAYMENT_ID = /^pay_[A-Za-z0-9]{6,40}$/;
/** Same rule as the server for the payment link id: "plink_" followed by 6–40 letters or digits. */
const PAYMENT_LINK_ID = /^plink_[A-Za-z0-9]{6,40}$/;
const POLL_EVERY_MS = 2000;
const POLL_ATTEMPTS = 15; // ~30 seconds

/**
 * /booking-success/:bookingId?razorpay_payment_id=pay_...&razorpay_payment_link_id=plink_...
 *
 * What happens after paying:
 *  1. Razorpay redirects the customer here.
 *  2. We ask our server to verify the payment (it double-checks with Razorpay itself, so a
 *     faked URL can't confirm anything).
 *  3. Confirmation travels through Kafka (payment -> booking service), which takes a moment.
 *     So we poll the booking every 2 seconds until it's no longer PENDING, for up to ~30 s.
 *
 * Razorpay also notifies our server directly (webhook), so even if the customer closes this tab,
 * the booking is still confirmed.
 */
@Component({
  selector: 'app-payment-return-page',
  imports: [DatePipe, RouterLink, MoneyPipe, Loading, ErrorState],
  templateUrl: './payment-return-page.html',
  styleUrl: './payment-return-page.scss',
})
export class PaymentReturnPage {
  readonly bookingId = input.required<string>();
  // Query parameter names chosen by Razorpay.
  readonly razorpay_payment_id = input<string>();
  readonly razorpay_payment_link_id = input<string>();

  private readonly bookingApi = inject(BookingApi);

  protected readonly booking = rxResource({
    params: () => ({
      id: Number(this.bookingId()),
      paymentId: this.razorpay_payment_id() ?? null,
      linkId: this.razorpay_payment_link_id() ?? null,
    }),
    stream: ({ params }) => this.confirm(params.id, params.paymentId, params.linkId),
  });

  protected readonly status = computed(() =>
    this.booking.hasValue() ? this.booking.value().status : null,
  );
  protected readonly errorText = computed(() => errorMessage(this.booking.error()));

  private confirm(
    bookingId: number,
    paymentId: string | null,
    linkId: string | null,
  ): Observable<Booking> {
    // Verifying is best effort: if it fails, the webhook will still settle the payment.
    const verify$ =
      paymentId && PAYMENT_ID.test(paymentId)
        ? this.bookingApi
            .verifyPayment({
              razorpayPaymentId: paymentId,
              // Only sent when well-formed; the server re-checks it with Razorpay anyway.
              ...(linkId && PAYMENT_LINK_ID.test(linkId) ? { razorpayPaymentLinkId: linkId } : {}),
            })
            .pipe(
              ignoreElements(),
              catchError(() => of()),
            )
        : of();

    // Fetch the booking every 2 s. takeWhile stops at the first one that isn't PENDING (or after
    // the last attempt; `true` = include that final one). last() emits only that final booking,
    // so the page keeps showing "Confirming your payment" until we know the outcome.
    const poll$ = timer(0, POLL_EVERY_MS).pipe(
      switchMap(() => this.bookingApi.get(bookingId)),
      takeWhile(
        (booking, attempt) => booking.status === 'PENDING' && attempt < POLL_ATTEMPTS - 1,
        true,
      ),
      last(),
    );

    return concat(
      defer(() => verify$),
      poll$,
    );
  }
}
