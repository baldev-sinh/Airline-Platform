import { FlightInstanceCabin, SeatInstance } from '../../core/models/api-models';

/**
 * Turns the flat list of seats from the API into rows for drawing a seat map:
 *
 *        A  B  C     D  E  F
 *   1   [ ][ ][ ]   [ ][ ][ ]
 *   2   [ ][ ][ ]   [ ][ ][ ]
 *
 * The seat map tells us how many seats are left and right of the aisle; the columns come from
 * the seats themselves. A missing seat (e.g. no seat 1B) becomes an empty gap.
 */
export interface SeatRow {
  readonly row: number;
  readonly left: readonly (SeatInstance | null)[];
  readonly right: readonly (SeatInstance | null)[];
}

export interface SeatLayout {
  readonly leftColumns: readonly string[];
  readonly rightColumns: readonly string[];
  readonly rows: readonly SeatRow[];
}

/** "12A" -> { row: 12, column: "A" }. Used when the nested `seat` object is missing. */
export function parseSeatNumber(seatNumber: string): { row: number; column: string } | null {
  const match = /^(\d+)([A-Z])$/i.exec(seatNumber.trim());
  return match ? { row: Number(match[1]), column: match[2].toUpperCase() } : null;
}

function position(seat: SeatInstance): { row: number; column: string } | null {
  if (seat.seat?.seatRow && seat.seat.columnLetter) {
    return { row: seat.seat.seatRow, column: String(seat.seat.columnLetter).toUpperCase() };
  }
  return parseSeatNumber(seat.seatNumber);
}

export function buildSeatLayout(cabin: FlightInstanceCabin): SeatLayout {
  const byPosition = new Map<string, SeatInstance>();
  const columns = new Set<string>();
  const rows = new Set<number>();

  for (const seat of cabin.seats) {
    const pos = position(seat);
    if (pos) {
      byPosition.set(`${pos.row}${pos.column}`, seat);
      columns.add(pos.column);
      rows.add(pos.row);
    }
  }

  // The seat service letters columns A, B, C... from the left. When the seat map says how many
  // seats a row has, use that, so a column with no seats at all still keeps the aisle in place.
  const left = cabin.seatMap?.leftSeatsPerRow ?? 0;
  const right = cabin.seatMap?.rightSeatsPerRow ?? 0;
  for (let i = 0; i < left + right && i < 26; i++) {
    columns.add(String.fromCharCode(65 + i)); // 65 = "A"
  }

  const sortedColumns = [...columns].sort();
  // If the seat map doesn't say where the aisle is, split the columns in half.
  const leftCount = left || Math.ceil(sortedColumns.length / 2);
  const leftColumns = sortedColumns.slice(0, leftCount);
  const rightColumns = sortedColumns.slice(leftCount);

  const seatAt = (row: number, column: string) => byPosition.get(`${row}${column}`) ?? null;

  return {
    leftColumns,
    rightColumns,
    rows: [...rows]
      .sort((a, b) => a - b)
      .map((row) => ({
        row,
        left: leftColumns.map((column) => seatAt(row, column)),
        right: rightColumns.map((column) => seatAt(row, column)),
      })),
  };
}

export function isSelectable(seat: SeatInstance): boolean {
  return seat.status === 'AVAILABLE';
}

/**
 * Picks seats for a group automatically ("Choose seats for me"). The customer didn't choose, so we never
 * spend their money without reason:
 *
 *   1. Price first: the cheapest possible set of seats (often free middle seats).
 *   2. Then together: seats side by side in one row, if they cost no more than that.
 *   3. Then front to back, left to right.
 *
 * Example with free middle seats and paid windows/aisles: one traveller gets 1B, two get 1B + 1E (both free,
 * same row, either side of the aisle). If every seat costs the same, a group sits together as before.
 * Returns seat ids, or fewer ids than requested if the cabin is nearly full.
 */
export function autoAssignSeats(
  layout: SeatLayout,
  count: number,
  exclude: ReadonlySet<number> = new Set(),
): number[] {
  const free = (seat: SeatInstance | null): seat is SeatInstance =>
    !!seat && isSelectable(seat) && !exclude.has(seat.id);
  // Whole paise, so totals compare exactly (0.1 + 0.2 !== 0.3 in floating point).
  const paise = (seat: SeatInstance) => Math.round((seat.price ?? 0) * 100);
  const totalPaise = (seats: readonly SeatInstance[]) =>
    seats.reduce((sum, s) => sum + paise(s), 0);

  // 1. The cheapest seats. Array.sort is stable, so seats with the same price keep cabin order (front first).
  const inCabinOrder = layout.rows.flatMap((row) => [...row.left, ...row.right]).filter(free);
  const cheapest = [...inCabinOrder].sort((a, b) => paise(a) - paise(b)).slice(0, count);
  const lowestTotal = totalPaise(cheapest);

  // 2. Side by side, front to back, but only if it costs no more than the cheapest choice.
  for (const row of layout.rows) {
    for (const block of [row.left, row.right]) {
      let run: SeatInstance[] = []; // the last `count` free seats in a row, without gaps
      for (const seat of block) {
        run = free(seat) ? [...run, seat].slice(-count) : [];
        if (run.length === count && totalPaise(run) <= lowestTotal) {
          return run.map((s) => s.id);
        }
      }
    }
  }

  // 3. Otherwise the cheapest seats, even if they are not next to each other.
  return cheapest.map((seat) => seat.id);
}
