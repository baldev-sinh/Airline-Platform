import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BookingApi } from '../../core/api/booking-api';
import { errorMessage } from '../../core/http/api-error';
import { EmptyState, ErrorState, Loading } from '../../shared/async-state';
import { BOOKING_STATUS } from '../../shared/labels';
import { MoneyPipe } from '../../shared/money-pipe';
import { TRIP_GROUPS, TripGroup, groupOf } from './trip-groups';

@Component({
  selector: 'app-trips-page',
  imports: [DatePipe, RouterLink, MoneyPipe, Loading, ErrorState, EmptyState],
  templateUrl: './trips-page.html',
  styleUrl: './trips-page.scss',
})
export class TripsPage {
  private readonly bookingApi = inject(BookingApi);

  protected readonly bookings = rxResource({ stream: () => this.bookingApi.myBookings() });
  protected readonly groups = TRIP_GROUPS;
  protected readonly statusMeta = BOOKING_STATUS;
  protected readonly group = signal<TripGroup>('upcoming');

  protected readonly all = computed(() => (this.bookings.hasValue() ? this.bookings.value() : []));
  protected readonly counts = computed(() => {
    const counts: Record<TripGroup, number> = { upcoming: 0, past: 0, cancelled: 0 };
    for (const booking of this.all()) {
      counts[groupOf(booking)]++;
    }
    return counts;
  });
  protected readonly visible = computed(() => {
    const list = this.all().filter((b) => groupOf(b) === this.group());
    // Upcoming: soonest first. Past and cancelled: most recent first.
    return this.group() === 'upcoming'
      ? list.sort((a, b) => a.departureTime.localeCompare(b.departureTime))
      : list.sort((a, b) => b.departureTime.localeCompare(a.departureTime));
  });
  protected readonly errorText = computed(() => errorMessage(this.bookings.error()));
}
