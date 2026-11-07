/**
 * TypeScript shapes of the JSON the backend sends and receives.
 *
 * Each interface mirrors a Java class in `common-lib` (named in the comment above it), so if the
 * backend changes a field, this is the one file to update. Only the fields the web app uses are
 * listed. Extra fields from the server are harmless.
 *
 * Conventions used by the backend:
 * - Dates without a zone ("2026-11-20T09:30:00") are LOCAL airport times. Show them as they are;
 *   never convert them to the browser's time zone.
 * - Dates ending in "Z" are UTC instants (for example when a booking was made).
 * - Money is a decimal with 2 places. The server always computes the amount that is charged.
 *   The client only shows estimates.
 */

/** ISO-8601 local date-time without a zone, e.g. "2026-11-20T09:30:00". Airport local time. */
export type LocalDateTimeString = string;
/** ISO-8601 date, e.g. "2026-11-20". */
export type LocalDateString = string;
/** ISO-8601 UTC instant, e.g. "2026-11-07T10:15:30Z". */
export type InstantString = string;

// ---------- Enums (Java enums arrive as their names) ----------

export type UserRole = 'ROLE_USER' | 'ROLE_AIRLINE_OWNER' | 'ROLE_SYSTEM_ADMIN';
export type CabinClassType = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
export type PaymentStatus =
  'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'REFUNDED' | 'PROCESSING';
export type SeatAvailabilityStatus = 'AVAILABLE' | 'HELD' | 'BOOKED' | 'OCCUPIED' | 'BLOCKED';
export type SeatType = 'WINDOW' | 'AISLE' | 'MIDDLE' | 'EXTRA_LEGROOM';
export type TicketStatus = 'BOOKED' | 'CANCELLED' | 'USED' | 'REFUNDED' | 'EXPIRED';
export type FlightStatus =
  | 'SCHEDULED'
  | 'BOARDING'
  | 'DEPARTED'
  | 'IN_AIR'
  | 'LANDED'
  | 'ARRIVED'
  | 'DELAYED'
  | 'CANCELLED'
  | 'DIVERTED'
  | 'COMPLETED';
export type AncillaryType = 'BAGGAGE' | 'TRAVEL_PROTECTION';

// ---------- Auth & users ----------

/** LoginRequest */
export interface LoginRequest {
  email: string;
  password: string;
}

/** SignupRequest. `role` is left out on purpose: the public site only creates customer accounts. */
export interface SignupRequest {
  fullName: string;
  email: string;
  password: string;
  phone?: string;
}

/** UserDTO */
export interface User {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: UserRole;
  lastLogin: LocalDateTimeString | null;
}

/** AuthResponse */
export interface AuthResponse {
  jwt: string;
  message: string | null;
  title: string | null;
  user: User;
}

// ---------- Locations ----------

/** CityResponse */
export interface City {
  id: number;
  name: string;
  cityCode: string;
  countryName: string;
  countryCode: string;
}

/** AirportResponse */
export interface Airport {
  id: number;
  iataCode: string;
  name: string;
  detailedName: string | null;
  timeZone: string | null;
  city: City | null;
}

/** AirlineDropdownItem */
export interface AirlineSummary {
  id: number;
  name: string;
  iataCode: string;
  logoUrl: string | null;
}

// ---------- Flights & fares ----------

/** BaggagePolicyResponse */
export interface BaggagePolicy {
  cabinBaggageMaxWeight: number | null;
  cabinBaggagePieces: number | null;
  checkInBaggageMaxWeight: number | null;
  checkInBaggagePieces: number | null;
}

/** FareRulesResponse */
export interface FareRules {
  isRefundable: boolean | null;
  isChangeable: boolean | null;
  changeFee: number | null;
  cancellationFee: number | null;
}

/** FareResponse */
export interface Fare {
  id: number;
  name: string;
  flightId: number;
  cabinClassId: number;
  cabinClass: CabinClassType | null;
  baseFare: number;
  taxesAndFees: number;
  airlineFees: number;
  /** Price for ONE passenger, taxes included. */
  totalPrice: number;
  fareLabel: string | null;
  priorityBoarding: boolean | null;
  priorityCheckin: boolean | null;
  complimentaryMeals: boolean | null;
  freeDateChange: boolean | null;
  partialRefund: boolean | null;
  fullRefund: boolean | null;
  loungeAccess: boolean | null;
  extraSeatSpace: boolean | null;
  advanceSeatSelection: boolean | null;
  fareRules: FareRules | null;
  baggagePolicy: BaggagePolicy | null;
}

/** FlightInstanceResponse: one dated departure of a flight. */
export interface FlightInstance {
  id: number;
  flightId: number;
  flightNumber: string;
  airlineId: number;
  airlineName: string;
  airlineLogo: string | null;
  aircraftModal: string | null; // (sic) spelled this way by the backend
  departureAirport: Airport;
  arrivalAirport: Airport;
  departureDateTime: LocalDateTimeString;
  arrivalDateTime: LocalDateTimeString;
  formattedDuration: string | null;
  totalSeats: number;
  availableSeats: number;
  status: FlightStatus;
  /** Lowest fare in the searched cabin. Present in search results only. */
  fare: Fare | null;
}

// ---------- Seats ----------

/** SeatResponse: the physical seat on the aircraft. */
export interface Seat {
  id: number;
  seatNumber: string;
  seatRow: number;
  columnLetter: string;
  seatType: SeatType;
  isEmergencyExit: boolean | null;
  hasExtraLegroom: boolean | null;
}

/** SeatInstanceResponse: a seat on ONE dated flight, with its live status and price. */
export interface SeatInstance {
  id: number;
  seatNumber: string;
  seatType: string | null;
  seat: Seat | null;
  price: number | null;
  status: SeatAvailabilityStatus;
}

/** SeatMapResponse (only the layout fields we draw with). */
export interface SeatMapLayout {
  totalRows: number | null;
  leftSeatsPerRow: number | null;
  rightSeatsPerRow: number | null;
}

/** FlightInstanceCabinResponse: one cabin (e.g. Economy) on a dated flight, with all its seats. */
export interface FlightInstanceCabin {
  id: number;
  flightInstanceId: number;
  cabinClassType: CabinClassType;
  cabinClass: { id: number; name: string } | null;
  seats: SeatInstance[];
  seatMap: SeatMapLayout | null;
  availableSeats: number | null;
  canBook: boolean | null;
}

// ---------- Extras ----------

/** MealResponse */
export interface Meal {
  id: number;
  code: string;
  name: string;
  description: string | null;
  mealType: string | null;
  dietaryRestriction: string | null;
}

/** FlightMealResponse: a meal offered on a flight, at a price. */
export interface FlightMeal {
  id: number;
  flightId: number;
  meal: Meal;
  available: boolean | null;
  price: number;
}

/** AncillaryResponse */
export interface Ancillary {
  id: number;
  type: AncillaryType;
  subType: string | null;
  name: string;
  description: string | null;
}

/** FlightCabinAncillaryResponse: an extra (bag, protection) offered on a flight + cabin. */
export interface FlightCabinAncillary {
  id: number;
  flightId: number;
  cabinClassId: number;
  ancillary: Ancillary;
  available: boolean | null;
  maxQuantity: number | null;
  price: number;
  includedInFare: boolean | null;
}

// ---------- Bookings ----------

/** ContactInfo */
export interface ContactInfo {
  email: string;
  phone: string;
}

/** PassengerRequest */
export interface PassengerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth: LocalDateString;
  gender: Gender;
  nationality?: string;
  seatInstanceId: number;
}

/** BookingRequest */
export interface BookingRequest {
  flightId: number;
  flightInstanceId: number;
  cabinClass: CabinClassType;
  fareId: number;
  passengers: PassengerRequest[];
  contactInfo: ContactInfo;
  /** FlightCabinAncillary ids. Repeat an id to buy more than one. */
  ancillaryIds: number[];
  /** FlightMeal ids, one entry per meal. */
  mealIds: number[];
}

/** PaymentInitiateResponse: what POST /api/bookings returns. */
export interface PaymentInitiateResponse {
  paymentId: number;
  amount: number;
  description: string | null;
  checkoutUrl: string | null;
  message: string | null;
  success: boolean | null;
}

/** PassengerResponse */
export interface Passenger {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string | null;
  email: string;
  gender: Gender;
  dateOfBirth: LocalDateString;
  seatInstanceId: number | null;
}

/** TicketResponse */
export interface Ticket {
  id: number;
  ticketNumber: string;
  status: TicketStatus;
  issuedAt: LocalDateTimeString | null;
  passengerId: number;
  passengerFirstName: string;
  passengerLastName: string;
}

/** BookingResponse */
export interface Booking {
  id: number;
  bookingReference: string;
  flightId: number;
  flightNumber: string;
  flightName: string | null;
  departureAirport: string;
  arrivalAirport: string;
  departureTime: LocalDateTimeString;
  arrivalTime: LocalDateTimeString;
  flightDuration: string | null;
  status: BookingStatus;
  bookingDate: InstantString;
  passengers: Passenger[];
  seatInstances: SeatInstance[] | null;
  ancillaries: FlightCabinAncillary[] | null;
  meals: FlightMeal[] | null;
  tickets: Ticket[] | null;
  paymentStatus: PaymentStatus | null;
  paymentLink: string | null;
  paymentId: number | null;
  fareName: string | null;
  totalPassengers: number | null;
  currency: string | null;
  fareAmount: number | null;
  seatAmount: number | null;
  ancillaryAmount: number | null;
  mealAmount: number | null;
  totalAmount: number | null;
  isUpcoming: boolean | null;
  isPast: boolean | null;
  cancellationReason: string | null;
  contactInfo: ContactInfo | null;
}

// ---------- Payments ----------

/** PaymentVerifyRequest */
export interface PaymentVerifyRequest {
  razorpayPaymentId: string;
  /** From the redirect URL; lets the server find our payment if Razorpay's payment has no notes. */
  razorpayPaymentLinkId?: string;
}

/** PaymentDTO */
export interface Payment {
  id: number;
  bookingId: number;
  status: PaymentStatus;
  amount: number;
  failureReason: string | null;
}
