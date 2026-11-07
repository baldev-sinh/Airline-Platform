import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { makeCabin, makeExtra, makeFare, makeFlight, makeMeal } from '../../../testing/fixtures';
import { BookingApi } from '../../core/api/booking-api';
import { ExtrasApi } from '../../core/api/extras-api';
import { FlightApi } from '../../core/api/flight-api';
import { SeatApi } from '../../core/api/seat-api';
import { AuthStore } from '../../core/auth/auth-store';
import { CheckoutStore } from './checkout-store';

describe('CheckoutStore', () => {
  let store: CheckoutStore;

  beforeEach(async () => {
    // The store's dependencies are replaced with fakes returning fixed data, so the test runs
    // without a server and checks only the store's own logic.
    TestBed.configureTestingModule({
      providers: [
        CheckoutStore,
        {
          provide: FlightApi,
          useValue: {
            flightInstance: () => of(makeFlight()),
            fares: () => of([makeFare(), makeFare({ id: 11, name: 'Flexi', totalPrice: 7900 })]),
          },
        },
        { provide: SeatApi, useValue: { cabin: () => of(makeCabin(3)) } },
        {
          provide: ExtrasApi,
          useValue: { meals: () => of([makeMeal(1, 350)]), extras: () => of([makeExtra(9, 1200)]) },
        },
        { provide: BookingApi, useValue: {} },
        {
          provide: AuthStore,
          useValue: {
            user: () => ({
              fullName: 'Asha Verma',
              email: 'asha@example.com',
              phone: '+919876543210',
            }),
          },
        },
      ],
    });
    store = TestBed.inject(CheckoutStore);
    await firstValueFrom(store.load(500, 'ECONOMY', 2, 11));
  });

  it('starts with the fare chosen on the results page', () => {
    expect(store.selectedFare()?.name).toBe('Flexi');
    expect(store.seatIds()).toEqual([null, null]);
  });

  it('pre-fills the first traveller and the contact from the account', () => {
    expect(store.passengerForms[0].getRawValue()).toMatchObject({
      firstName: 'Asha',
      lastName: 'Verma',
    });
    expect(store.travellerForm.controls.contact.getRawValue().email).toBe('asha@example.com');
  });

  it('assigns seats traveller by traveller, and a second click frees the seat', () => {
    store.toggleSeat(11); // 1A for traveller 1, then moves on to traveller 2
    expect(store.activePassenger()).toBe(1);
    store.toggleSeat(12); // 1B for traveller 2
    expect(store.seatIds()).toEqual([11, 12]);

    store.activePassenger.set(0);
    store.toggleSeat(12); // taken by traveller 2: ignored
    expect(store.seatIds()).toEqual([11, 12]);
    store.toggleSeat(11); // own seat: freed
    expect(store.seatIds()).toEqual([null, 12]);
  });

  it('does not let the customer skip past an invalid step', () => {
    store.goTo('review');
    expect(store.step()).toBe('travellers'); // traveller details are still missing
  });

  it('builds the booking request the API expects', () => {
    store.passengerForms[0].patchValue({ gender: 'FEMALE', dateOfBirth: '1994-03-18' });
    store.passengerForms[1].patchValue({
      firstName: ' Ravi ',
      lastName: 'Kumar',
      gender: 'MALE',
      dateOfBirth: '1990-01-01',
      nationality: 'in',
    });
    store.travellerForm.controls.contact.patchValue({ phone: '+91 98765-43210' });
    store.autoAssign();
    store.setMeal(1, 1);
    store.setExtraQuantity(makeExtra(9, 1200), 2);

    const request = store.buildRequest();

    expect(request).toMatchObject({
      flightId: 100,
      flightInstanceId: 500,
      cabinClass: 'ECONOMY',
      fareId: 11,
    });
    expect(request.contactInfo).toEqual({ email: 'asha@example.com', phone: '+919876543210' });
    expect(request.passengers[1]).toMatchObject({
      firstName: 'Ravi',
      nationality: 'IN',
      phone: '+919876543210',
    });
    expect(request.passengers.map((p) => p.seatInstanceId)).toEqual([11, 12]);
    expect(request.mealIds).toEqual([1]); // traveller 1 chose no meal
    expect(request.ancillaryIds).toEqual([9, 9]); // quantity 2 = the id twice
    expect(store.price().total).toBe(7900 * 2 + 350 + 2400);
  });

  it('never allows more extras than the airline offers', () => {
    store.setExtraQuantity(makeExtra(9, 1200, { maxQuantity: 2 }), 5);
    expect(store.extraQuantities()[9]).toBe(2);
  });
});
