import { Fare, FlightCabinAncillary, FlightMeal, SeatInstance } from '../../core/models/api-models';

/**
 * The price the customer sees while choosing.
 *
 * IMPORTANT: this is an ESTIMATE for display only. The server recalculates the price from its
 * own data when the booking is created, and the payment amount always comes from the server.
 * A customer who edits this code in their browser can change what they see, never what they pay.
 *
 * It follows the same rules as the backend's BookingPriceCalculator:
 *   fare per passenger x passengers + each seat's price + each meal + each extra x quantity
 */
export interface PriceEstimate {
  readonly fare: number;
  readonly seats: number;
  readonly meals: number;
  readonly extras: number;
  readonly total: number;
}

export interface PriceInput {
  readonly fare: Fare | null;
  readonly passengers: number;
  readonly seats: readonly (SeatInstance | undefined)[];
  readonly meals: readonly (FlightMeal | undefined)[];
  readonly extras: readonly { readonly item: FlightCabinAncillary; readonly quantity: number }[];
}

/**
 * Money in JavaScript: 0.1 + 0.2 === 0.30000000000000004. Rounding to whole paise after each
 * step keeps displayed totals exact to 2 decimals.
 */
const toPaise = (amount: number | null | undefined) => Math.round((amount ?? 0) * 100);
const fromPaise = (paise: number) => paise / 100;

export function estimatePrice(input: PriceInput): PriceEstimate {
  const fare = toPaise(input.fare?.totalPrice) * input.passengers;
  const seats = input.seats.reduce((sum, seat) => sum + toPaise(seat?.price), 0);
  const meals = input.meals.reduce((sum, meal) => sum + toPaise(meal?.price), 0);
  const extras = input.extras.reduce(
    (sum, { item, quantity }) => sum + (item.includedInFare ? 0 : toPaise(item.price) * quantity),
    0,
  );
  return {
    fare: fromPaise(fare),
    seats: fromPaise(seats),
    meals: fromPaise(meals),
    extras: fromPaise(extras),
    total: fromPaise(fare + seats + meals + extras),
  };
}
