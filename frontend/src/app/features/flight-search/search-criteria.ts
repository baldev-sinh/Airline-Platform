import { CabinClassType } from '../../core/models/api-models';
import { CABIN_LABELS } from '../../shared/labels';
import { todayIso } from '../../shared/validators';

/**
 * A flight search as it appears in the address bar:
 *   /flights?from=BOM&to=DEL&date=2026-11-20&pax=2&cabin=ECONOMY
 *
 * Keeping the search in the URL (instead of only in memory) means results can be bookmarked,
 * shared, refreshed, and the browser's Back button works as people expect.
 * Airport CODES are used (not database ids) because they are readable and stable.
 */
export interface SearchCriteria {
  readonly from: string;
  readonly to: string;
  readonly date: string;
  readonly passengers: number;
  readonly cabin: CabinClassType;
}

export const MAX_PASSENGERS = 9;

export type SearchQueryParams = Record<'from' | 'to' | 'date' | 'pax' | 'cabin', string>;

export function toQueryParams(criteria: SearchCriteria): SearchQueryParams {
  return {
    from: criteria.from,
    to: criteria.to,
    date: criteria.date,
    pax: String(criteria.passengers),
    cabin: criteria.cabin,
  };
}

/**
 * Reads criteria from URL values. Anyone can type anything into a URL, so every value is checked.
 * Returns null when the search is unusable; the page then asks the user to search again.
 */
export function parseCriteria(raw: {
  from?: string | null;
  to?: string | null;
  date?: string | null;
  pax?: string | null;
  cabin?: string | null;
}): SearchCriteria | null {
  const from = (raw.from ?? '').trim().toUpperCase();
  const to = (raw.to ?? '').trim().toUpperCase();
  const date = (raw.date ?? '').trim();
  const passengers = Number(raw.pax ?? '1');
  const cabin = (raw.cabin ?? 'ECONOMY').toUpperCase();

  const isAirportCode = (code: string) => /^[A-Z0-9]{3}$/.test(code);
  const isValid =
    isAirportCode(from) &&
    isAirportCode(to) &&
    from !== to &&
    /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    date >= todayIso() &&
    Number.isInteger(passengers) &&
    passengers >= 1 &&
    passengers <= MAX_PASSENGERS &&
    cabin in CABIN_LABELS;

  return isValid ? { from, to, date, passengers, cabin: cabin as CabinClassType } : null;
}
