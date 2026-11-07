#!/usr/bin/env node
/**
 * Creates a realistic, bookable demo network through the public API: real Indian airports, two fictional
 * airlines with aircraft and seat maps, daily flight schedules, fares with rules and baggage, meals and extras.
 * The data lives in demo-data.mjs.
 *
 * Safe to run again at any time: everything is looked up first and only created if missing. Run it regularly
 * (e.g. daily) to keep flights scheduled DAYS ahead; each run only adds the missing days.
 *
 *   node scripts/seed-demo/seed-demo.mjs [--api http://localhost:5000] [--days 45] [--env-file docker-compose/.env]
 *
 * Settings (environment variables, or KEY=VALUE lines in the env file; real environment variables win):
 *   SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD  a system admin (falls back to BOOTSTRAP_ADMIN_EMAIL / _PASSWORD)
 *   DEMO_OWNER_PASSWORD                     password for the demo airlines' owner accounts (10+ characters);
 *                                           the accounts are created with it on the first run
 *   SEED_API_URL                            same as --api
 *
 * Needs Node 18+ (built-in fetch); no npm packages. Everything goes through the gateway, so the services'
 * own validation, ownership checks and Kafka flows (seat inventory) run exactly as for real users.
 */
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { AIRCRAFT_TYPES, AIRLINES, AIRPORTS, FARES } from './demo-data.mjs';

// ============================================================================ options and settings

const here = dirname(fileURLToPath(import.meta.url));
const args = parseArgs(process.argv.slice(2));
loadEnvFile(args['env-file'] ?? resolve(here, '../../docker-compose/.env'));

const API = (args.api ?? process.env.SEED_API_URL ?? 'http://localhost:5000').replace(/\/+$/, '');
const DAYS = Number(args.days ?? 45);
const ADMIN_EMAIL = process.env.SEED_ADMIN_EMAIL || process.env.BOOTSTRAP_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.SEED_ADMIN_PASSWORD || process.env.BOOTSTRAP_ADMIN_PASSWORD;
const OWNER_PASSWORD = process.env.DEMO_OWNER_PASSWORD;

/** Each schedule request generates one dated flight per day; chunks keep each request well under timeouts. */
const SCHEDULE_CHUNK_DAYS = 30;
const ALL_DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) out[argv[i].slice(2)] = argv[i + 1]?.startsWith('--') ? 'true' : argv[++i];
  }
  return out;
}

/** Minimal .env reader: KEY=VALUE lines, quotes stripped. Variables already set in the environment win. */
function loadEnvFile(path) {
  if (!existsSync(path)) return;
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const m = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/.exec(line);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^(['"])(.*)\1$/, '$2');
  }
}

// ============================================================================ HTTP

class ApiError extends Error {
  constructor(status, code, detail, path) {
    super(`${status} ${code ?? ''} ${detail ?? ''} (${path})`.trim());
    Object.assign(this, { status, code, detail, path });
  }
}

/**
 * Calls the API and returns the parsed JSON (or null for an empty body).
 * `notFound: null` makes a 404 return null instead of throwing (used by lookups).
 * Retries 502/503/504 briefly: right after start-up the gateway may not have discovered every service yet.
 */
async function api(method, path, { token, body, notFound } = {}) {
  for (let attempt = 1; ; attempt++) {
    const response = await fetch(API + path, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
        ...(token && { Authorization: `Bearer ${token}` }),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if ([502, 503, 504].includes(response.status) && attempt < 6) {
      await sleep(3000);
      continue;
    }
    const text = await response.text();
    const json = text ? safeJson(text) : null;
    if (response.ok) return json;
    if (response.status === 404 && notFound !== undefined) return notFound;
    throw new ApiError(response.status, json?.code, json?.detail ?? text.slice(0, 200), `${method} ${path}`);
  }
}

const safeJson = (text) => { try { return JSON.parse(text); } catch { return null; } };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
/** Spring pages come as { content: [...] }; some endpoints return plain arrays. */
const items = (result) => (Array.isArray(result) ? result : result?.content ?? []);

// ============================================================================ find-or-create

const stats = { created: 0, existing: 0 };

/**
 * The heart of the script: look the thing up; create it only if it isn't there. If creating reports a
 * conflict (409), someone else created it meanwhile, so look it up again.
 */
async function ensure(label, find, create) {
  // find(refresh): refresh=true means "don't trust any list fetched earlier, fetch it again".
  const found = await find(false);
  if (found) {
    stats.existing++;
    return found;
  }
  try {
    const made = await create();
    stats.created++;
    console.log(`  + ${label}`);
    return made;
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      const again = await find(true); // fresh data this time
      if (again) {
        stats.existing++;
        return again;
      }
    }
    throw e;
  }
}

/** Fetches a list once and reuses it for lookups; listing(true) fetches it again (after a conflict). */
function listOf(path, token) {
  let cached;
  return async (refresh) => {
    if (!cached || refresh) cached = items(await api('GET', path, { token, notFound: [] }));
    return cached;
  };
}

// ============================================================================ dates (India time)

/** Today's date in India as YYYY-MM-DD, whatever the server's time zone. */
function todayInIndia() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
}
function addDays(isoDate, days) {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

// ============================================================================ steps

async function login(email, password) {
  try {
    return (await api('POST', '/auth/login', { body: { email, password } })).jwt;
  } catch (e) {
    if (e instanceof ApiError && (e.status === 401 || e.status === 400)) return null;
    throw e;
  }
}

/** Signs in, or creates the airline owner account the first time. */
async function ownerToken(owner) {
  const existing = await login(owner.email, OWNER_PASSWORD);
  if (existing) return existing;
  try {
    const signup = await api('POST', '/auth/signup', {
      body: { fullName: owner.fullName, email: owner.email, password: OWNER_PASSWORD, role: 'ROLE_AIRLINE_OWNER' },
    });
    console.log(`  + owner account ${owner.email}`);
    stats.created++;
    return signup.jwt;
  } catch (e) {
    if (e instanceof ApiError && e.code === 'EMAIL_TAKEN') {
      throw new Error(`${owner.email} exists but DEMO_OWNER_PASSWORD doesn't match its password.`);
    }
    throw e;
  }
}

async function seedAirports(adminToken) {
  console.log('Cities and airports');
  const cities = listOf('/api/cities?size=1000');
  const airports = listOf('/api/airports');
  const airportIds = {};
  const cityIds = {};
  for (const a of AIRPORTS) {
    const city = await ensure(
      `city ${a.city} (${a.code})`,
      async (r) => (await cities(r)).find((c) => c.cityCode === a.code),
      () => api('POST', '/api/cities', {
        token: adminToken,
        body: { name: a.city, cityCode: a.code, countryCode: a.countryCode, countryName: a.countryName, timeZoneOffset: a.timeZoneOffset },
      }),
    );
    cityIds[a.code] = city.id;
    const airport = await ensure(
      `airport ${a.code} ${a.name}`,
      async (r) => (await airports(r)).find((x) => x.iataCode === a.code),
      () => api('POST', '/api/airports', {
        token: adminToken,
        body: { iataCode: a.code, name: a.name, timeZone: a.timeZone, cityId: city.id, geoCode: { latitude: a.lat, longitude: a.lon } },
      }),
    );
    airportIds[a.code] = airport.id;
  }
  return { airportIds, cityIds };
}

async function seedAirline(def, adminToken, { airportIds, cityIds }) {
  console.log(`${def.name} (${def.iataCode})`);
  const token = await ownerToken(def.owner);

  // ---- The airline, approved by the admin ----
  let airline = await ensure(
    `airline ${def.name}`,
    () => api('GET', '/api/airlines/admin', { token, notFound: null }),
    () => api('POST', '/api/airlines', {
      token,
      body: { iataCode: def.iataCode, icaoCode: def.icaoCode, name: def.name, headquartersCityId: cityIds[def.headquarters], supportEmail: def.supportEmail },
    }),
  );
  if (airline.status === 'PENDING_APPROVAL') {
    airline = await api('POST', `/api/airlines/${airline.id}/approve`, { token: adminToken });
    console.log(`  + approved ${def.name}`);
  } else if (airline.status !== 'ACTIVE') {
    throw new Error(`${def.name} is ${airline.status}; an admin must reactivate it.`);
  }

  // ---- Fleet: aircraft, cabins and seat maps (seat maps generate the physical seats) ----
  const aircraftList = listOf('/api/aircrafts', token);
  const fleet = {}; // registration -> { id, cabins: { ECONOMY: id, BUSINESS: id } }
  for (const plane of def.aircraft) {
    const type = AIRCRAFT_TYPES[plane.type];
    const seatsIn = (name) => type.cabins.filter((c) => c.name === name).reduce((n, c) => n + c.rows * (c.left + c.right), 0);
    const total = type.cabins.reduce((n, c) => n + c.rows * (c.left + c.right), 0);
    const aircraft = await ensure(
      `aircraft ${plane.code} (${plane.type}, ${total} seats)`,
      async (r) => (await aircraftList(r)).find((a) => a.code === plane.code),
      () => api('POST', '/api/aircrafts', {
        token,
        body: {
          code: plane.code, model: plane.type, manufacturer: type.manufacturer, seatingCapacity: total,
          economySeats: seatsIn('ECONOMY') || undefined, businessSeats: seatsIn('BUSINESS') || undefined,
          rangeKm: type.rangeKm, yearOfManufacture: plane.year, status: 'ACTIVE', isAvailable: true,
        },
      }),
    );
    const cabinList = listOf(`/api/cabin-classes/aircraft/${aircraft.id}`, token);
    fleet[plane.code] = { id: aircraft.id, cabins: {} };
    for (const c of type.cabins) {
      const cabin = await ensure(
        `  ${plane.code} ${c.name} cabin`,
        async (r) => (await cabinList(r)).find((x) => x.name === c.name),
        () => api('POST', '/api/cabin-classes', {
          token,
          body: {
            name: c.name, code: c.code, description: c.name === 'BUSINESS' ? 'Business' : 'Economy', aircraftId: aircraft.id,
            displayOrder: c.displayOrder, isActive: true, isBookable: true, typicalSeatPitch: c.pitch, typicalSeatWidth: c.width,
          },
        }),
      );
      fleet[plane.code].cabins[c.name] = cabin.id;
      await ensure(
        `  ${plane.code} ${c.name} seat map (${c.rows} rows, ${c.left}-${c.right})`,
        async () => {
          const map = await api('GET', `/api/seat-maps/cabin-class/${cabin.id}`, { token, notFound: null });
          return map && map.id ? map : null;
        },
        () => api('POST', '/api/seat-maps', {
          token,
          body: { name: `${plane.code}-${c.code}`, cabinClassId: cabin.id, totalRows: c.rows, leftSeatsPerRow: c.left, rightSeatsPerRow: c.right },
        }),
      );
    }
  }

  // ---- Catalogue: meals and extras the airline sells ----
  const mealList = listOf('/api/meals/airline', token);
  const meals = [];
  for (const m of def.meals) {
    const meal = await ensure(
      `meal ${m.name}`,
      // Older backends left `code` out of meal responses; fall back to the name there.
      async (r) => (await mealList(r)).find((x) => x.code === m.code || (x.code == null && x.name === m.name)),
      () => api('POST', '/api/meals', {
        token,
        body: { code: m.code, name: m.name, mealType: m.type, dietaryRestriction: m.diet ?? undefined, ingredients: m.ingredients, requiresAdvanceBooking: false },
      }),
    );
    meals.push({ id: meal.id, price: m.price });
  }
  const extraList = listOf('/api/ancillaries', token);
  const extras = [];
  for (const x of def.extras) {
    const extra = await ensure(
      `extra ${x.name}`,
      async (r) => (await extraList(r)).find((a) => a.name === x.name),
      () => api('POST', '/api/ancillaries', { token, body: { type: x.type, subType: x.subType, name: x.name, description: x.description } }),
    );
    extras.push({ id: extra.id, ...x });
  }

  // ---- Flights, with their fares, meals and extras ----
  const flightList = listOf('/api/flights/airline?size=500', token);
  const flights = [];
  for (const [number, from, to, dep, arr, registration, saver] of def.flights) {
    const plane = fleet[registration];
    const flight = await ensure(
      `flight ${number} ${from}-${to} ${dep}`,
      async (r) => (await flightList(r)).find((f) => f.flightNumber === number),
      () => api('POST', '/api/flights', {
        token,
        body: { flightNumber: number, aircraftId: plane.id, departureAirportId: airportIds[from], arrivalAirportId: airportIds[to] },
      }),
    );
    flights.push({ id: flight.id, number, from, to, dep, arr });

    for (const [cabinName, cabinId] of Object.entries(plane.cabins)) {
      const existingFares = listOf(`/api/fares/flight/${flight.id}/cabin-class/${cabinId}`, token);
      for (const f of FARES[cabinName] ?? []) {
        const total = Math.round((saver * f.factor) / f.roundTo) * f.roundTo;
        const airlineFees = cabinName === 'BUSINESS' ? 500 : 250;
        const taxes = Math.round(total * 0.12);
        const fare = await ensure(
          `  ${number} ${f.name} fare ₹${total}`,
          async (r) => (await existingFares(r)).find((x) => x.name === f.name),
          () => api('POST', '/api/fares', {
            token,
            body: {
              name: f.name, rbdCode: f.rbd, flightId: flight.id, cabinClassId: cabinId,
              baseFare: total - taxes - airlineFees, taxesAndFees: taxes, airlineFees, fareLabel: f.label, ...f.flags,
            },
          }),
        );
        await ensure(
          `  ${number} ${f.name} fare rules`,
          () => api('GET', `/api/fare-rules/fare/${fare.id}`, { token, notFound: null }).then((r) => (r?.id ? r : null)),
          () => api('POST', '/api/fare-rules', { token, body: { ruleName: `${f.name} rules`, fareId: fare.id, airlineId: airline.id, ...f.rules } }),
        );
        await ensure(
          `  ${number} ${f.name} baggage`,
          () => api('GET', `/api/baggage-policies/fare/${fare.id}`, { token, notFound: null }).then((r) => (r?.id ? r : null)),
          () => api('POST', '/api/baggage-policies', {
            token,
            body: {
              name: `${f.name} baggage`, fareId: fare.id,
              cabinBaggageMaxWeight: f.baggage.cabinKg, cabinBaggagePieces: f.baggage.cabinPieces,
              checkInBaggageMaxWeight: f.baggage.checkInKg, checkInBaggagePieces: f.baggage.checkInPieces,
              freeCheckedBagsAllowance: f.baggage.checkInPieces, priorityBaggage: !!f.baggage.priority, extraBaggageAllowance: false,
            },
          }),
        );
      }
    }

    const flightMeals = listOf(`/api/flight-meals/flight/${flight.id}`, token);
    for (const meal of meals) {
      await ensure(
        `  ${number} meal ${meal.id}`,
        async (r) => (await flightMeals(r)).find((x) => x.meal?.id === meal.id),
        () => api('POST', '/api/flight-meals', { token, body: { flightId: flight.id, mealId: meal.id, available: true, price: meal.price } }),
      );
    }

    // Extras are sold in Economy; Business already includes generous baggage.
    const economyId = plane.cabins.ECONOMY;
    const sold = listOf(`/api/flight-cabin-ancillaries/flight/${flight.id}/cabin/${economyId}`, token);
    for (const x of extras) {
      await ensure(
        `  ${number} extra ${x.name}`,
        async (r) => (await sold(r)).find((s) => s.ancillary?.id === x.id),
        () => api('POST', '/api/flight-cabin-ancillaries', {
          token,
          body: { flightId: flight.id, cabinClassId: economyId, ancillaryId: x.id, available: true, maxQuantity: x.maxQuantity, price: x.price, includedInFare: false },
        }),
      );
    }
  }

  // ---- Schedules last: they create the dated flights, and seat-service then builds each one's seat inventory ----
  const today = todayInIndia();
  const until = addDays(today, DAYS);
  const schedules = items(await api('GET', '/api/flight-schedules', { token, notFound: [] }));
  let datedFlights = 0;
  for (const f of flights) {
    const lastScheduled = schedules.filter((s) => s.flightId === f.id).map((s) => s.endDate).sort().at(-1);
    let start = lastScheduled && lastScheduled >= today ? addDays(lastScheduled, 1) : today;
    while (start <= until) {
      const end = [addDays(start, SCHEDULE_CHUNK_DAYS - 1), until].sort()[0];
      await api('POST', '/api/flight-schedules', {
        token,
        body: { flightId: f.id, departureTime: `${f.dep}:00`, arrivalTime: `${f.arr}:00`, startDate: start, endDate: end, operatingDays: ALL_DAYS, isActive: true },
      });
      const days = (Date.parse(end) - Date.parse(start)) / 86_400_000 + 1;
      datedFlights += days;
      stats.created++;
      console.log(`  + ${f.number} scheduled daily ${start} → ${end}`);
      start = addDays(end, 1);
    }
  }
  if (datedFlights === 0) console.log(`  schedules already reach ${until}`);
  return { token, flights, datedFlights };
}

/** Seat inventory is built asynchronously (Kafka). Wait until a sample flight can actually be booked. */
async function waitForSeats(token, flight) {
  const tomorrow = addDays(todayInIndia(), 1);
  for (let i = 0; i < 40; i++) {
    const instances = items(await api('GET', `/api/flight-instances?flightId=${flight.id}&size=50`, { token, notFound: [] }));
    const instance = instances.find((x) => x.departureDateTime?.startsWith(tomorrow)) ?? instances[0];
    if (instance) {
      const cabins = items(await api('GET', `/api/flight-instance-cabins/flight-instance/${instance.id}`, { token, notFound: [] }));
      const seats = cabins.reduce((n, c) => n + (c.seats?.length ?? 0), 0);
      if (seats > 0) return seats;
    }
    await sleep(3000);
  }
  return 0;
}

// ============================================================================ main

async function main() {
  if (!ADMIN_EMAIL || !ADMIN_PASSWORD) throw new Error('Set SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD (or BOOTSTRAP_ADMIN_*).');
  if (!OWNER_PASSWORD || OWNER_PASSWORD.length < 10) throw new Error('Set DEMO_OWNER_PASSWORD (at least 10 characters).');
  if (!Number.isInteger(DAYS) || DAYS < 1 || DAYS > 330) throw new Error('--days must be between 1 and 330.');

  console.log(`Seeding ${API}: flights scheduled ${DAYS} days ahead (until ${addDays(todayInIndia(), DAYS)})\n`);
  const adminToken = await login(ADMIN_EMAIL, ADMIN_PASSWORD);
  if (!adminToken) throw new Error(`Admin sign-in failed for ${ADMIN_EMAIL}.`);

  const places = await seedAirports(adminToken);
  let datedFlights = 0;
  let sample;
  for (const def of AIRLINES) {
    const result = await seedAirline(def, adminToken, places);
    datedFlights += result.datedFlights;
    sample ??= { token: result.token, flight: result.flights[0] };
  }

  console.log('\nWaiting for seat inventory (built asynchronously through Kafka)...');
  const seats = await waitForSeats(sample.token, sample.flight);
  console.log(seats > 0 ? `  ${sample.flight.number} has ${seats} seats on sale` : '  WARNING: no seat inventory yet; check seat-service and Kafka');

  const tomorrow = addDays(todayInIndia(), 1);
  const search = await api('GET',
    `/api/flights/search?departureAirportId=${places.airportIds.DEL}&arrivalAirportId=${places.airportIds.BOM}&departureDate=${tomorrow}&passengers=1&cabinClass=ECONOMY`);
  const found = items(search).map((f) => `${f.flightNumber} ${f.departureDateTime.slice(11, 16)} from ₹${f.fare?.totalPrice}`);

  console.log(`\nDone: ${stats.created} created, ${stats.existing} already there, ${datedFlights} dated flights added.`);
  console.log(`Public search DEL → BOM on ${tomorrow}: ${found.length ? found.join(', ') : 'NO FLIGHTS FOUND'}`);
  if (!found.length) process.exitCode = 1;
}

main().catch((e) => {
  console.error(`\nSeeding failed: ${e.message}`);
  process.exit(1);
});
