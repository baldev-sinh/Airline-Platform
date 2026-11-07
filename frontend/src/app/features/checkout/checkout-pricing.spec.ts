import { makeExtra, makeFare, makeMeal, makeSeat } from '../../../testing/fixtures';
import { estimatePrice } from './checkout-pricing';

describe('estimatePrice', () => {
  it('adds fare per passenger, seats, meals and extras', () => {
    const price = estimatePrice({
      fare: makeFare({ totalPrice: 5300 }),
      passengers: 2,
      seats: [makeSeat('1A', { price: 350 }), makeSeat('1B', { price: 0 })],
      meals: [makeMeal(1, 350), undefined],
      extras: [{ item: makeExtra(9, 1200), quantity: 2 }],
    });
    expect(price).toEqual({ fare: 10600, seats: 350, meals: 350, extras: 2400, total: 13700 });
  });

  it('does not charge for extras already included in the fare', () => {
    const price = estimatePrice({
      fare: makeFare({ totalPrice: 1000 }),
      passengers: 1,
      seats: [],
      meals: [],
      extras: [{ item: makeExtra(9, 1200, { includedInFare: true }), quantity: 1 }],
    });
    expect(price.extras).toBe(0);
    expect(price.total).toBe(1000);
  });

  it('avoids floating-point surprises', () => {
    const price = estimatePrice({
      fare: makeFare({ totalPrice: 0.1 }),
      passengers: 1,
      seats: [makeSeat('1A', { price: 0.2 })],
      meals: [],
      extras: [],
    });
    expect(price.total).toBe(0.3); // plain JS would give 0.30000000000000004
  });

  it('is zero before a fare is chosen', () => {
    expect(
      estimatePrice({ fare: null, passengers: 3, seats: [], meals: [], extras: [] }).total,
    ).toBe(0);
  });
});
