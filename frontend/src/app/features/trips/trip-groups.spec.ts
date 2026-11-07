import { Booking } from '../../core/models/api-models';
import { groupOf } from './trip-groups';

const now = new Date('2030-05-01T12:00:00');
const booking = (overrides: Partial<Booking>) =>
  ({
    status: 'CONFIRMED',
    departureTime: '2030-06-01T09:00:00',
    isPast: null,
    ...overrides,
  }) as Booking;

describe('groupOf', () => {
  it('sorts bookings into upcoming, past and cancelled', () => {
    expect(groupOf(booking({}), now)).toBe('upcoming');
    expect(groupOf(booking({ status: 'PENDING' }), now)).toBe('upcoming');
    expect(groupOf(booking({ departureTime: '2030-04-01T09:00:00' }), now)).toBe('past');
    expect(groupOf(booking({ status: 'COMPLETED' }), now)).toBe('past');
    expect(groupOf(booking({ status: 'CANCELLED' }), now)).toBe('cancelled');
  });
});
