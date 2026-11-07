/**
 * The demo network created by seed-demo.mjs. Edit this file to change routes, prices or meals;
 * the script itself only knows how to create things, not what to create.
 *
 * Airports, cities and coordinates are real. The airlines, aircraft registrations and flight numbers
 * are fictional.
 */

const IST = { countryCode: 'IN', countryName: 'India', timeZoneOffset: '+05:30', timeZone: 'Asia/Kolkata' };

/** City (IATA city code) and its main airport. */
export const AIRPORTS = [
  { code: 'DEL', city: 'Delhi', name: 'Indira Gandhi International Airport', lat: 28.5562, lon: 77.1, ...IST },
  { code: 'BOM', city: 'Mumbai', name: 'Chhatrapati Shivaji Maharaj International Airport', lat: 19.0896, lon: 72.8656, ...IST },
  { code: 'BLR', city: 'Bengaluru', name: 'Kempegowda International Airport', lat: 13.1986, lon: 77.7066, ...IST },
  { code: 'HYD', city: 'Hyderabad', name: 'Rajiv Gandhi International Airport', lat: 17.2403, lon: 78.4294, ...IST },
  { code: 'MAA', city: 'Chennai', name: 'Chennai International Airport', lat: 12.9941, lon: 80.1709, ...IST },
  { code: 'CCU', city: 'Kolkata', name: 'Netaji Subhas Chandra Bose International Airport', lat: 22.6547, lon: 88.4467, ...IST },
  { code: 'GOI', city: 'Goa', name: 'Goa International Airport (Dabolim)', lat: 15.3808, lon: 73.8314, ...IST },
];

/** Aircraft types: cabins and their seat layout (rows x left/right seats of the aisle). */
export const AIRCRAFT_TYPES = {
  A320neo: {
    manufacturer: 'Airbus',
    rangeKm: 6300,
    cabins: [
      { name: 'BUSINESS', code: 'J', rows: 2, left: 2, right: 2, pitch: 38, width: 21, displayOrder: 1 },
      { name: 'ECONOMY', code: 'Y', rows: 20, left: 3, right: 3, pitch: 30, width: 17, displayOrder: 2 },
    ],
  },
  'ATR 72-600': {
    manufacturer: 'ATR',
    rangeKm: 1500,
    cabins: [{ name: 'ECONOMY', code: 'Y', rows: 18, left: 2, right: 2, pitch: 30, width: 18, displayOrder: 1 }],
  },
};

/**
 * Fare families, priced from each route's Saver price (`saver` below, total per adult incl. taxes).
 * `factor` multiplies the Saver price; `roundTo` keeps prices tidy.
 */
export const FARES = {
  ECONOMY: [
    {
      name: 'Saver', rbd: 'V', label: 'Lowest price', factor: 1, roundTo: 50,
      flags: { advanceSeatSelection: true },
      rules: { isRefundable: false, isChangeable: true, changeFee: 3000, cancellationFee: 0, changeDeadlineHours: 4 },
      baggage: { cabinKg: 7, cabinPieces: 1, checkInKg: 15, checkInPieces: 1 },
    },
    {
      name: 'Flexi', rbd: 'M', label: 'Free date change', factor: 1.35, roundTo: 50,
      flags: { advanceSeatSelection: true, freeDateChange: true, partialRefund: true, complimentaryMeals: true, priorityCheckin: true },
      rules: { isRefundable: true, isChangeable: true, changeFee: 0, cancellationFee: 1500, refundDeadlineDays: 1, changeDeadlineHours: 2 },
      baggage: { cabinKg: 7, cabinPieces: 1, checkInKg: 20, checkInPieces: 1 },
    },
  ],
  BUSINESS: [
    {
      name: 'Business', rbd: 'J', label: 'Extra space and lounge access', factor: 3.2, roundTo: 100,
      flags: {
        advanceSeatSelection: true, preferredSeatChoice: true, extraSeatSpace: true, priorityBoarding: true,
        priorityCheckin: true, fastTrackSecurity: true, complimentaryMeals: true, premiumMealChoice: true,
        complimentaryBeverages: true, loungeAccess: true, freeDateChange: true, fullRefund: true,
      },
      rules: { isRefundable: true, isChangeable: true, changeFee: 0, cancellationFee: 0, refundDeadlineDays: 0, changeDeadlineHours: 1 },
      baggage: { cabinKg: 10, cabinPieces: 2, checkInKg: 35, checkInPieces: 2, priority: true },
    },
  ],
};

export const AIRLINES = [
  {
    name: 'Baldev Airways',
    iataCode: 'BD',
    icaoCode: 'BDV',
    headquarters: 'DEL',
    supportEmail: 'care@baldevairways.example',
    owner: { fullName: 'Baldev Airways Operations', email: 'ops@baldevairways.example' },
    aircraft: [
      { code: 'VT-BDA', type: 'A320neo', year: 2022 },
      { code: 'VT-BDB', type: 'A320neo', year: 2022 },
      { code: 'VT-BDC', type: 'A320neo', year: 2023 },
      { code: 'VT-BDD', type: 'A320neo', year: 2024 },
    ],
    meals: [
      { code: 'VGML', name: 'Paneer tikka wrap', type: 'VEG', diet: 'Vegetarian', ingredients: 'Grilled paneer, mint chutney, onion', price: 350 },
      { code: 'NVML', name: 'Chicken biryani', type: 'NON_VEG', diet: null, ingredients: 'Hyderabadi-style, with raita', price: 450 },
      { code: 'UPMA', name: 'Masala upma', type: 'VEG', diet: 'Vegetarian', ingredients: 'With coconut chutney', price: 300 },
      { code: 'FRUIT', name: 'Seasonal fruit platter', type: 'VEG', diet: 'Vegan', ingredients: 'Fresh cut fruit', price: 250 },
    ],
    extras: [
      { type: 'BAGGAGE', subType: 'EXTRA_BAG', name: 'Extra 15 kg checked bag', description: 'Add before you fly and save at the airport', price: 1800, maxQuantity: 2 },
      { type: 'BAGGAGE', subType: 'EXTRA_BAG', name: 'Extra 5 kg checked bag', description: 'For a little more room', price: 700, maxQuantity: 1 },
      { type: 'TRAVEL_PROTECTION', subType: 'TRIP_PROTECTION', name: 'Trip protection', description: 'Covers delays, missed connections and lost baggage', price: 299, maxQuantity: 1 },
    ],
    // number, from, to, departure, arrival (local time; an arrival before departure means the next day), aircraft, Saver price
    flights: [
      ['BD101', 'DEL', 'BOM', '06:30', '08:40', 'VT-BDA', 5200],
      ['BD102', 'BOM', 'DEL', '09:30', '11:40', 'VT-BDA', 5200],
      ['BD105', 'DEL', 'BOM', '18:00', '20:10', 'VT-BDB', 5600],
      ['BD106', 'BOM', 'DEL', '21:00', '23:10', 'VT-BDB', 5400],
      ['BD201', 'BOM', 'BLR', '07:15', '09:00', 'VT-BDC', 4600],
      ['BD202', 'BLR', 'BOM', '10:00', '11:45', 'VT-BDC', 4600],
      ['BD301', 'DEL', 'BLR', '08:00', '10:50', 'VT-BDD', 6200],
      ['BD302', 'BLR', 'DEL', '12:00', '14:50', 'VT-BDD', 6200],
      ['BD401', 'BOM', 'GOI', '13:00', '14:10', 'VT-BDC', 3400],
      ['BD402', 'GOI', 'BOM', '15:00', '16:10', 'VT-BDC', 3400],
      ['BD501', 'DEL', 'CCU', '16:30', '18:45', 'VT-BDD', 5900],
      ['BD502', 'CCU', 'DEL', '19:45', '22:10', 'VT-BDD', 5900],
      ['BD601', 'DEL', 'HYD', '22:45', '01:00', 'VT-BDA', 5600],
      ['BD602', 'HYD', 'DEL', '06:00', '08:15', 'VT-BDA', 5600],
    ],
  },
  {
    name: 'Coastal Wings',
    iataCode: 'CW',
    icaoCode: 'CWG',
    headquarters: 'BLR',
    supportEmail: 'help@coastalwings.example',
    owner: { fullName: 'Coastal Wings Operations', email: 'ops@coastalwings.example' },
    aircraft: [
      { code: 'VT-CWA', type: 'ATR 72-600', year: 2021 },
      { code: 'VT-CWB', type: 'ATR 72-600', year: 2023 },
    ],
    meals: [
      { code: 'VSND', name: 'Veg club sandwich', type: 'VEG', diet: 'Vegetarian', ingredients: 'With masala chips', price: 250 },
      { code: 'CSND', name: 'Chicken tikka sandwich', type: 'NON_VEG', diet: null, ingredients: 'With masala chips', price: 300 },
    ],
    extras: [
      { type: 'BAGGAGE', subType: 'EXTRA_BAG', name: 'Extra 10 kg checked bag', description: 'Regional flights: add it now', price: 1200, maxQuantity: 1 },
    ],
    flights: [
      ['CW311', 'BLR', 'HYD', '09:15', '10:35', 'VT-CWA', 3300],
      ['CW312', 'HYD', 'BLR', '11:15', '12:35', 'VT-CWA', 3300],
      ['CW411', 'BLR', 'MAA', '17:40', '18:45', 'VT-CWA', 2900],
      ['CW412', 'MAA', 'BLR', '19:30', '20:35', 'VT-CWA', 2900],
      ['CW511', 'BOM', 'GOI', '08:20', '09:30', 'VT-CWB', 3200],
      ['CW512', 'GOI', 'BOM', '10:10', '11:20', 'VT-CWB', 3200],
    ],
  },
];
