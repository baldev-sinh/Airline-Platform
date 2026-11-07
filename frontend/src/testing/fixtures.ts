/**
 * Small factories for test data. Each returns a complete, valid object; a test overrides only
 * the fields it cares about:  makeFlight({ departureDateTime: '2030-01-01T07:00:00' })
 *
 * Only used by *.spec.ts files (excluded from the app build in tsconfig.app.json).
 */
import {
  Airport,
  Fare,
  FlightCabinAncillary,
  FlightInstance,
  FlightInstanceCabin,
  FlightMeal,
  SeatInstance,
} from '../app/core/models/api-models';

export function makeAirport(overrides: Partial<Airport> = {}): Airport {
  return {
    id: 1,
    iataCode: 'BOM',
    name: 'Chhatrapati Shivaji Maharaj International',
    detailedName: null,
    timeZone: 'Asia/Kolkata',
    city: { id: 1, name: 'Mumbai', cityCode: 'BOM', countryName: 'India', countryCode: 'IN' },
    ...overrides,
  };
}

export function makeFare(overrides: Partial<Fare> = {}): Fare {
  return {
    id: 10,
    name: 'Saver',
    flightId: 100,
    cabinClassId: 7,
    cabinClass: 'ECONOMY',
    baseFare: 4500,
    taxesAndFees: 650,
    airlineFees: 150,
    totalPrice: 5300,
    fareLabel: 'Lowest price',
    priorityBoarding: false,
    priorityCheckin: false,
    complimentaryMeals: false,
    freeDateChange: false,
    partialRefund: false,
    fullRefund: false,
    loungeAccess: false,
    extraSeatSpace: false,
    advanceSeatSelection: true,
    fareRules: null,
    baggagePolicy: null,
    ...overrides,
  };
}

export function makeFlight(overrides: Partial<FlightInstance> = {}): FlightInstance {
  return {
    id: 500,
    flightId: 100,
    flightNumber: 'SA101',
    airlineId: 1,
    airlineName: 'Baldev Airways',
    airlineLogo: null,
    aircraftModal: 'A320neo',
    departureAirport: makeAirport(),
    arrivalAirport: makeAirport({
      id: 2,
      iataCode: 'DEL',
      city: { id: 2, name: 'Delhi', cityCode: 'DEL', countryName: 'India', countryCode: 'IN' },
    }),
    departureDateTime: '2030-05-01T09:30:00',
    arrivalDateTime: '2030-05-01T11:45:00',
    formattedDuration: '2h 15min',
    totalSeats: 60,
    availableSeats: 60,
    status: 'SCHEDULED',
    fare: makeFare(),
    ...overrides,
  };
}

export function makeSeat(seatNumber: string, overrides: Partial<SeatInstance> = {}): SeatInstance {
  const row = Number(seatNumber.slice(0, -1));
  const column = seatNumber.slice(-1);
  return {
    id: row * 10 + column.charCodeAt(0) - 64, // 1A -> 11, 1B -> 12, 2A -> 21 ...
    seatNumber,
    seatType: null,
    seat: {
      id: 1,
      seatNumber,
      seatRow: row,
      columnLetter: column,
      seatType: 'MIDDLE',
      isEmergencyExit: false,
      hasExtraLegroom: false,
    },
    price: 0,
    status: 'AVAILABLE',
    ...overrides,
  };
}

/** A cabin with `rows` rows of seats A-F, aisle after C. */
export function makeCabin(
  rows: number,
  overrides: Partial<FlightInstanceCabin> = {},
): FlightInstanceCabin {
  const seats: SeatInstance[] = [];
  for (let r = 1; r <= rows; r++) {
    for (const c of 'ABCDEF') {
      seats.push(makeSeat(`${r}${c}`));
    }
  }
  return {
    id: 1,
    flightInstanceId: 500,
    cabinClassType: 'ECONOMY',
    cabinClass: { id: 7, name: 'ECONOMY' },
    seats,
    seatMap: { totalRows: rows, leftSeatsPerRow: 3, rightSeatsPerRow: 3 },
    availableSeats: seats.length,
    canBook: true,
    ...overrides,
  };
}

export function makeMeal(id: number, price: number): FlightMeal {
  return {
    id,
    flightId: 100,
    price,
    available: true,
    meal: {
      id,
      code: `M${id}`,
      ingredients: null,
      name: `Meal ${id}`,
      description: null,
      mealType: 'VEG',
      dietaryRestriction: null,
    },
  };
}

export function makeExtra(
  id: number,
  price: number,
  overrides: Partial<FlightCabinAncillary> = {},
): FlightCabinAncillary {
  return {
    id,
    flightId: 100,
    cabinClassId: 7,
    price,
    available: true,
    maxQuantity: 2,
    includedInFare: false,
    ancillary: {
      id,
      type: 'BAGGAGE',
      subType: 'EXTRA_BAG',
      name: 'Extra 15 kg bag',
      description: null,
    },
    ...overrides,
  };
}
