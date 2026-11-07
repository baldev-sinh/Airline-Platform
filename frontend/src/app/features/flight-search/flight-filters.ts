import { FlightInstance } from '../../core/models/api-models';

/**
 * Pure functions for filtering and sorting search results. "Pure" means: same input, same
 * output, no side effects. That makes them trivial to unit test (see flight-filters.spec.ts).
 */

export type TimeWindow = 'morning' | 'afternoon' | 'evening' | 'night';
export type SortOrder = 'departure' | 'price' | 'duration' | 'arrival';

/** Same hour ranges the backend uses for its departureTimeRange filter. */
export const TIME_WINDOWS: readonly { value: TimeWindow; label: string; hours: string }[] = [
  { value: 'morning', label: 'Morning', hours: '06:00–11:59' },
  { value: 'afternoon', label: 'Afternoon', hours: '12:00–17:59' },
  { value: 'evening', label: 'Evening', hours: '18:00–20:59' },
  { value: 'night', label: 'Night', hours: '21:00–05:59' },
];

export const SORT_OPTIONS: readonly { value: SortOrder; label: string }[] = [
  { value: 'departure', label: 'Earliest departure' },
  { value: 'price', label: 'Lowest price' },
  { value: 'duration', label: 'Shortest duration' },
  { value: 'arrival', label: 'Earliest arrival' },
];

export interface FlightFilters {
  readonly windows: readonly TimeWindow[];
  readonly airlineIds: readonly number[];
  readonly sort: SortOrder;
}

/** "2026-11-20T09:30:00" -> 9. Reads the text directly: these are airport local times. */
export function hourOf(localDateTime: string): number {
  return Number(localDateTime.slice(11, 13));
}

export function timeWindowOf(localDateTime: string): TimeWindow {
  const hour = hourOf(localDateTime);
  if (hour >= 6 && hour < 12) return 'morning';
  if (hour >= 12 && hour < 18) return 'afternoon';
  if (hour >= 18 && hour < 21) return 'evening';
  return 'night';
}

/** "2h 15min" -> 135. Unknown formats sort last. */
export function durationMinutes(formatted: string | null | undefined): number {
  if (!formatted) {
    return Number.MAX_SAFE_INTEGER;
  }
  const hours = /(\d+)\s*h/.exec(formatted)?.[1] ?? '0';
  const minutes = /(\d+)\s*m/.exec(formatted)?.[1] ?? '0';
  return Number(hours) * 60 + Number(minutes);
}

/** Days between departure and arrival dates, for the "+1" marker on overnight flights. */
export function dayOffset(departure: string, arrival: string): number {
  const toDay = (value: string) =>
    Date.UTC(+value.slice(0, 4), +value.slice(5, 7) - 1, +value.slice(8, 10));
  return Math.round((toDay(arrival) - toDay(departure)) / 86_400_000);
}

export function priceOf(flight: FlightInstance): number {
  return flight.fare?.totalPrice ?? Number.MAX_SAFE_INTEGER;
}

export function applyFilters(
  flights: readonly FlightInstance[],
  filters: FlightFilters,
): FlightInstance[] {
  const matches = flights.filter(
    (flight) =>
      (filters.windows.length === 0 ||
        filters.windows.includes(timeWindowOf(flight.departureDateTime))) &&
      (filters.airlineIds.length === 0 || filters.airlineIds.includes(flight.airlineId)),
  );

  const compare: Record<SortOrder, (a: FlightInstance, b: FlightInstance) => number> = {
    departure: (a, b) => a.departureDateTime.localeCompare(b.departureDateTime),
    arrival: (a, b) => a.arrivalDateTime.localeCompare(b.arrivalDateTime),
    price: (a, b) => priceOf(a) - priceOf(b),
    duration: (a, b) => durationMinutes(a.formattedDuration) - durationMinutes(b.formattedDuration),
  };
  // Ties keep departure order, so the list never "jumps" unexpectedly.
  return matches.sort((a, b) => compare[filters.sort](a, b) || compare.departure(a, b));
}

/** The airlines present in the results, for the airline filter checkboxes. */
export function airlinesIn(
  flights: readonly FlightInstance[],
): { id: number; name: string; count: number }[] {
  const byId = new Map<number, { id: number; name: string; count: number }>();
  for (const flight of flights) {
    const entry = byId.get(flight.airlineId) ?? {
      id: flight.airlineId,
      name: flight.airlineName,
      count: 0,
    };
    entry.count++;
    byId.set(flight.airlineId, entry);
  }
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name));
}
