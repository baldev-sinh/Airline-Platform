import { BookingStatus, CabinClassType, Gender } from '../core/models/api-models';

/**
 * Human-friendly names for the enum values the API sends ("PREMIUM_ECONOMY" -> "Premium Economy").
 * `Record<CabinClassType, string>` makes TypeScript complain if a new enum value is added on the
 * server and someone forgets to label it here.
 */
export const CABIN_LABELS: Record<CabinClassType, string> = {
  ECONOMY: 'Economy',
  PREMIUM_ECONOMY: 'Premium Economy',
  BUSINESS: 'Business',
  FIRST: 'First',
};

export const CABIN_OPTIONS = Object.entries(CABIN_LABELS).map(([value, label]) => ({
  value: value as CabinClassType,
  label,
}));

export const GENDER_LABELS: Record<Gender, string> = {
  FEMALE: 'Female',
  MALE: 'Male',
  OTHER: 'Other',
};

export interface StatusMeta {
  readonly label: string;
  /** Matches a `.badge--*` modifier in styles/_components.scss. */
  readonly tone: 'success' | 'warning' | 'danger' | 'neutral';
}

export const BOOKING_STATUS: Record<BookingStatus, StatusMeta> = {
  CONFIRMED: { label: 'Confirmed', tone: 'success' },
  PENDING: { label: 'Awaiting payment', tone: 'warning' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
  COMPLETED: { label: 'Flown', tone: 'neutral' },
};
