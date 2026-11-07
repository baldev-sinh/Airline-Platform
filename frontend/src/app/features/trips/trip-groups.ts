import { Booking } from '../../core/models/api-models';

export type TripGroup = 'upcoming' | 'past' | 'cancelled';

export const TRIP_GROUPS: readonly { id: TripGroup; label: string }[] = [
  { id: 'upcoming', label: 'Upcoming' },
  { id: 'past', label: 'Past' },
  { id: 'cancelled', label: 'Cancelled' },
];

/**
 * Which tab a booking belongs in. `now` is a parameter (not read inside) so tests can pass a
 * fixed time. Departure times are airport-local; comparing them to the browser's clock is an
 * approximation that is good enough for sorting trips into tabs.
 */
export function groupOf(booking: Booking, now: Date = new Date()): TripGroup {
  if (booking.status === 'CANCELLED') {
    return 'cancelled';
  }
  if (booking.status === 'COMPLETED' || booking.isPast === true) {
    return 'past';
  }
  return new Date(booking.departureTime) < now ? 'past' : 'upcoming';
}
