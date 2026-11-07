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
