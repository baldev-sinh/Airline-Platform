import { makeFare, makeFlight } from '../../../testing/fixtures';
import {
  airlinesIn,
  applyFilters,
  dayOffset,
  durationMinutes,
  timeWindowOf,
} from './flight-filters';

describe('flight filters', () => {
  const early = makeFlight({
    id: 1,
    departureDateTime: '2030-05-01T06:10:00',
    formattedDuration: '2h 30min',
    fare: makeFare({ totalPrice: 7000 }),
  });
  const noon = makeFlight({
    id: 2,
    departureDateTime: '2030-05-01T12:00:00',
    formattedDuration: '1h 55min',
    fare: makeFare({ totalPrice: 4200 }),
    airlineId: 2,
    airlineName: 'Blue Kite',
  });
  const late = makeFlight({
    id: 3,
    departureDateTime: '2030-05-01T23:30:00',
    formattedDuration: '2h 15min',
    fare: makeFare({ totalPrice: 5100 }),
  });
  const all = [late, noon, early];

  it('puts hours in the same windows as the backend', () => {
    expect(timeWindowOf('2030-05-01T05:59:00')).toBe('night');
    expect(timeWindowOf('2030-05-01T06:00:00')).toBe('morning');
    expect(timeWindowOf('2030-05-01T12:00:00')).toBe('afternoon');
    expect(timeWindowOf('2030-05-01T20:59:00')).toBe('evening');
    expect(timeWindowOf('2030-05-01T21:00:00')).toBe('night');
  });

  it('parses the server duration text', () => {
    expect(durationMinutes('2h 15min')).toBe(135);
    expect(durationMinutes('45min')).toBe(45);
    expect(durationMinutes('3h ')).toBe(180);
  });

  it('detects overnight arrivals', () => {
    expect(dayOffset('2030-05-01T23:30:00', '2030-05-02T01:45:00')).toBe(1);
    expect(dayOffset('2030-05-01T09:00:00', '2030-05-01T11:00:00')).toBe(0);
  });

  it('sorts by departure, price or duration', () => {
    const ids = (sort: 'departure' | 'price' | 'duration') =>
      applyFilters(all, { sort, windows: [], airlineIds: [] }).map((f) => f.id);
    expect(ids('departure')).toEqual([1, 2, 3]);
    expect(ids('price')).toEqual([2, 3, 1]);
    expect(ids('duration')).toEqual([2, 3, 1]);
  });

  it('filters by time of day and airline', () => {
    expect(
      applyFilters(all, { sort: 'departure', windows: ['morning', 'night'], airlineIds: [] }).map(
        (f) => f.id,
      ),
    ).toEqual([1, 3]);
    expect(
      applyFilters(all, { sort: 'departure', windows: [], airlineIds: [2] }).map((f) => f.id),
    ).toEqual([2]);
  });

  it('does not modify the original list', () => {
    applyFilters(all, { sort: 'price', windows: [], airlineIds: [] });
    expect(all.map((f) => f.id)).toEqual([3, 2, 1]);
  });

  it('lists the airlines found, with counts', () => {
    expect(airlinesIn(all)).toEqual([
      { id: 1, name: 'Baldev Airways', count: 2 },
      { id: 2, name: 'Blue Kite', count: 1 },
    ]);
  });
});
