import { DatePipe } from '@angular/common';
import { Component, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BookingApi } from '../../core/api/booking-api';
import { TRUSTED_PAYMENT_HOSTS } from '../../core/config/app-config';
import { errorMessage } from '../../core/http/api-error';
import { Booking } from '../../core/models/api-models';
import { isTrustedRedirect } from '../../core/util/safe-redirect';
import { ErrorState, Loading } from '../../shared/async-state';
import { BOOKING_STATUS } from '../../shared/labels';
import { MoneyPipe } from '../../shared/money-pipe';

/** /trips/:bookingId: itinerary, travellers, e-tickets, payment and actions for one booking. */
@Component({
  selector: 'app-trip-detail-page',
  imports: [DatePipe, RouterLink, MoneyPipe, Loading, ErrorState],
  templateUrl: './trip-detail-page.html',
  styleUrl: './trip-detail-page.scss',
})
export class TripDetailPage {
  readonly bookingId = input.required<string>();

  private readonly bookingApi = inject(BookingApi);
  private readonly trustedHosts = inject(TRUSTED_PAYMENT_HOSTS);
  private readonly cancelDialog = viewChild<ElementRef<HTMLDialogElement>>('cancelDialog');

  protected readonly booking = rxResource({
    params: () => Number(this.bookingId()),
    stream: ({ params: id }) => this.bookingApi.get(id),
  });

  protected readonly statusMeta = BOOKING_STATUS;
  protected readonly cancelling = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected readonly errorText = computed(() => errorMessage(this.booking.error()));

  /** One row per traveller with their seat and e-ticket number joined in. */
  protected readonly travellers = computed(() => {
    if (!this.booking.hasValue()) {
      return [];
    }
    const b = this.booking.value();
    return b.passengers.map((p) => ({
      ...p,
      seat: b.seatInstances?.find((s) => s.id === p.seatInstanceId)?.seatNumber ?? null,
      ticket: b.tickets?.find((t) => t.passengerId === p.id) ?? null,
    }));
  });

  /** A payment link is offered only if it points somewhere we trust (see safe-redirect.ts). */
  protected readonly payUrl = computed(() => {
    if (!this.booking.hasValue()) {
      return null;
    }
    const b = this.booking.value();
    const link = b.paymentLink;
    return b.status === 'PENDING' &&
      link &&
      isTrustedRedirect(link, this.trustedHosts, window.location.origin)
      ? link
      : null;
  });

  /**
   * Pending bookings can always be cancelled; ticketed ones until departure, under the fare's refund
   * rules (the server decides the refund and rejects what is not allowed).
   */
  protected readonly canCancel = computed(() => {
    if (!this.booking.hasValue()) {
      return false;
    }
    const b = this.booking.value();
    return b.status === 'PENDING' || (b.status === 'CONFIRMED' && b.isUpcoming === true);
  });

  protected openCancel(): void {
    this.actionError.set(null);
    this.cancelDialog()?.nativeElement.showModal(); // native modal: focus trap + Esc to close built in
  }

  protected closeCancel(): void {
    this.cancelDialog()?.nativeElement.close();
  }

  protected confirmCancel(booking: Booking): void {
    this.cancelling.set(true);
    this.bookingApi.cancel(booking.id).subscribe({
      next: (updated) => {
        this.booking.set(updated); // show the new status without reloading
        this.cancelling.set(false);
        this.closeCancel();
      },
      error: (err: unknown) => {
        this.actionError.set(errorMessage(err));
        this.cancelling.set(false);
      },
    });
  }

  protected print(): void {
    window.print();
  }
}
