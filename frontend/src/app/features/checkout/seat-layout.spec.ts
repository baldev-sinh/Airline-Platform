import { makeCabin, makeSeat } from '../../../testing/fixtures';
import { autoAssignSeats, buildSeatLayout, parseSeatNumber } from './seat-layout';

describe('seat layout', () => {
  it('splits columns at the aisle and orders rows', () => {
    const layout = buildSeatLayout(makeCabin(2));
    expect(layout.leftColumns).toEqual(['A', 'B', 'C']);
    expect(layout.rightColumns).toEqual(['D', 'E', 'F']);
    expect(layout.rows.map((r) => r.row)).toEqual([1, 2]);
    expect(layout.rows[0].left.map((s) => s?.seatNumber)).toEqual(['1A', '1B', '1C']);
  });

  it('leaves a gap where a seat does not exist', () => {
    const cabin = makeCabin(1);
    cabin.seats = cabin.seats.filter((s) => s.seatNumber !== '1B');
    expect(buildSeatLayout(cabin).rows[0].left[1]).toBeNull();
  });

  it('falls back to the seat number when the nested seat is missing', () => {
    expect(parseSeatNumber('12C')).toEqual({ row: 12, column: 'C' });
    const cabin = makeCabin(0, { seats: [makeSeat('3D', { seat: null })] });
    expect(buildSeatLayout(cabin).rows[0].row).toBe(3);
  });

  it('seats a group together when possible', () => {
    const cabin = makeCabin(2);
    // Row 1 left side has only two free seats in a row; three travellers need row 1 right side.
    cabin.seats = cabin.seats.map((s) =>
      s.seatNumber === '1B' ? { ...s, status: 'BOOKED' as const } : s,
    );
    const ids = autoAssignSeats(buildSeatLayout(cabin), 3);
    const numbers = ids.map((id) => cabin.seats.find((s) => s.id === id)?.seatNumber);
    expect(numbers).toEqual(['1D', '1E', '1F']);
  });

  it('falls back to any free seats, and never picks taken ones', () => {
    const cabin = makeCabin(1);
    cabin.seats = cabin.seats.map((s) =>
      ['1B', '1E'].includes(s.seatNumber) ? { ...s, status: 'HELD' as const } : s,
    );
    const numbers = autoAssignSeats(buildSeatLayout(cabin), 3).map(
      (id) => cabin.seats.find((s) => s.id === id)?.seatNumber,
    );
    expect(numbers).toEqual(['1A', '1C', '1D']);
  });
});

describe('auto seat choice prefers free seats', () => {
  // The seat service's real pricing: window 1,000, aisle 500, middle seats free.
  const PRICE: Record<string, number> = { A: 1000, B: 0, C: 500, D: 500, E: 0, F: 1000 };
  function pricedCabin(rows: number) {
    const cabin = makeCabin(rows);
    cabin.seats = cabin.seats.map((s) => ({ ...s, price: PRICE[s.seatNumber.slice(-1)] }));
    return cabin;
  }
  const pick = (cabin: ReturnType<typeof makeCabin>, count: number) =>
    autoAssignSeats(buildSeatLayout(cabin), count).map(
      (id) => cabin.seats.find((s) => s.id === id)?.seatNumber,
    );

  it('gives one traveller a free seat at the front, not a paid window', () => {
    expect(pick(pricedCabin(3), 1)).toEqual(['1B']);
  });

  it('gives two travellers free seats in the same row rather than paid seats together', () => {
    expect(pick(pricedCabin(3), 2)).toEqual(['1B', '1E']);
  });

  it('moves to the next row when the free seats in front are taken', () => {
    const cabin = pricedCabin(3);
    cabin.seats = cabin.seats.map((s) =>
      ['1B', '1E'].includes(s.seatNumber) ? { ...s, status: 'BOOKED' as const } : s,
    );
    expect(pick(cabin, 2)).toEqual(['2B', '2E']);
  });

  it('keeps a group together when that costs no more', () => {
    const cabin = pricedCabin(2);
    // Row 2 left side is free (e.g. a promotion): together and free beats free but apart.
    cabin.seats = cabin.seats.map((s) => (/^2[ABC]$/.test(s.seatNumber) ? { ...s, price: 0 } : s));
    expect(pick(cabin, 3)).toEqual(['2A', '2B', '2C']);
  });

  it('when there are not enough free seats, takes the cheapest paid ones next', () => {
    const cabin = pricedCabin(1); // one row: 2 free seats, 2 aisles at 500, 2 windows at 1,000
    expect(pick(cabin, 3)).toEqual(['1B', '1E', '1C']);
  });
});
