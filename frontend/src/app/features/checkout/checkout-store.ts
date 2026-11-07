import { Injectable, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, NonNullableFormBuilder, Validators } from '@angular/forms';
import { Observable, catchError, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { BookingApi } from '../../core/api/booking-api';
import { ExtrasApi } from '../../core/api/extras-api';
import { FlightApi } from '../../core/api/flight-api';
import { SeatApi } from '../../core/api/seat-api';
import { AuthStore } from '../../core/auth/auth-store';
import { ApiError } from '../../core/http/api-error';
import {
  BookingRequest,
  CabinClassType,
  Fare,
  FlightCabinAncillary,
  FlightInstance,
  FlightInstanceCabin,
  FlightMeal,
  Gender,
  PaymentInitiateResponse,
} from '../../core/models/api-models';
import { CABIN_LABELS } from '../../shared/labels';
import { normalizePhone, pastDate, phoneNumber } from '../../shared/validators';
import { estimatePrice } from './checkout-pricing';
import { SeatLayout, autoAssignSeats, buildSeatLayout } from './seat-layout';

/** Everything loaded from the server before checkout can start. */
export interface CheckoutContext {
  readonly flight: FlightInstance;
  readonly cabin: FlightInstanceCabin;
  readonly cabinClassId: number;
  readonly fares: readonly Fare[];
  readonly meals: readonly FlightMeal[];
  readonly extras: readonly FlightCabinAncillary[];
}

export type StepId = 'fare' | 'travellers' | 'seats' | 'extras' | 'review';

export const STEPS: readonly { id: StepId; label: string }[] = [
  { id: 'fare', label: 'Fare' },
  { id: 'travellers', label: 'Travellers' },
  { id: 'seats', label: 'Seats' },
  { id: 'extras', label: 'Extras' },
  { id: 'review', label: 'Review & pay' },
];

/** Typed shape of one traveller's form. */
export type PassengerForm = FormGroup<{
  firstName: FormControl<string>;
  lastName: FormControl<string>;
  gender: FormControl<Gender | ''>;
  dateOfBirth: FormControl<string>;
  nationality: FormControl<string>;
}>;

/**
 * All state of ONE checkout: what was loaded, and what the customer has chosen so far.
 *
 * It is provided by CheckoutPage (`providers: [CheckoutStore]`), not app-wide. Each checkout
 * gets a fresh store that is thrown away when the customer leaves the page, so no stale choices
 * can leak into the next booking. Every step component injects this same instance.
 */
@Injectable()
export class CheckoutStore {
  private readonly flightApi = inject(FlightApi);
  private readonly seatApi = inject(SeatApi);
  private readonly extrasApi = inject(ExtrasApi);
  private readonly bookingApi = inject(BookingApi);
  private readonly auth = inject(AuthStore);
  private readonly fb = inject(NonNullableFormBuilder);

  // ---------------- Loaded data ----------------
  readonly context = signal<CheckoutContext | null>(null);
  readonly passengerCount = signal(1);
  readonly cabinType = signal<CabinClassType>('ECONOMY');

  // ---------------- Customer choices ----------------
  readonly step = signal<StepId>('fare');
  readonly fareId = signal<number | null>(null);
  /** seatIds[i] is the seat of passenger i (null = not chosen yet). */
  readonly seatIds = signal<readonly (number | null)[]>([]);
  readonly activePassenger = signal(0);
  /** mealIds[i] is passenger i's meal (null = no meal). */
  readonly mealIds = signal<readonly (number | null)[]>([]);
  /** Extra id -> how many. */
  readonly extraQuantities = signal<Readonly<Record<number, number>>>({});
  readonly termsAccepted = signal(false);
  /** Shown on the seat step when the server says a seat was taken meanwhile. */
  readonly seatNotice = signal<string | null>(null);

  /** Traveller details. A reactive form, because it has many fields with validation rules. */
  readonly travellerForm = this.fb.group({
    contact: this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      phone: ['', [Validators.required, phoneNumber]],
    }),
    passengers: this.fb.array<PassengerForm>([]),
  });

  // ---------------- Derived values (recomputed automatically) ----------------
  readonly selectedFare = computed(
    () => this.context()?.fares.find((f) => f.id === this.fareId()) ?? null,
  );
  readonly seatLayout = computed<SeatLayout | null>(() => {
    const ctx = this.context();
    return ctx ? buildSeatLayout(ctx.cabin) : null;
  });
  readonly selectedSeats = computed(() => {
    const seats = this.context()?.cabin.seats ?? [];
    return this.seatIds().map((id) => seats.find((s) => s.id === id));
  });
  readonly selectedMeals = computed(() => {
    const meals = this.context()?.meals ?? [];
    return this.mealIds().map((id) => meals.find((m) => m.id === id));
  });
  readonly selectedExtras = computed(() =>
    (this.context()?.extras ?? [])
      .map((item) => ({ item, quantity: this.extraQuantities()[item.id] ?? 0 }))
      .filter((entry) => entry.quantity > 0),
  );
  readonly price = computed(() =>
    estimatePrice({
      fare: this.selectedFare(),
      passengers: this.passengerCount(),
      seats: this.selectedSeats(),
      meals: this.selectedMeals(),
      extras: this.selectedExtras(),
    }),
  );
  readonly allSeatsChosen = computed(
    () => this.seatIds().length > 0 && this.seatIds().every((id) => id !== null),
  );
  readonly stepIndex = computed(() => STEPS.findIndex((s) => s.id === this.step()));

  get passengerForms(): PassengerForm[] {
    return this.travellerForm.controls.passengers.controls;
  }

  // ---------------- Loading ----------------

  /**
   * Loads the flight, cabin, fares and extras, then resets all choices.
   * Meals and extras are optional: if that service is down, checkout still works without them.
   */
  load(
    flightInstanceId: number,
    cabin: CabinClassType,
    passengers: number,
    preferredFareId: number | null,
  ): Observable<CheckoutContext> {
    return forkJoin({
      flight: this.flightApi.flightInstance(flightInstanceId),
      cabin: this.seatApi.cabin(flightInstanceId, cabin),
    }).pipe(
      switchMap(({ flight, cabin: flightCabin }) => {
        if (!flightCabin?.cabinClass) {
          throw new ApiError(
            404,
            'CABIN_NOT_FOUND',
            `This flight has no ${CABIN_LABELS[cabin]} cabin.`,
          );
        }
        const cabinClassId = flightCabin.cabinClass.id;
        return forkJoin({
          fares: this.flightApi.fares(flight.flightId, cabinClassId),
          meals: this.extrasApi.meals(flight.flightId).pipe(catchError(() => of([]))),
          extras: this.extrasApi
            .extras(flight.flightId, cabinClassId)
            .pipe(catchError(() => of([]))),
        }).pipe(
          map((rest): CheckoutContext => ({ flight, cabin: flightCabin, cabinClassId, ...rest })),
        );
      }),
      tap((context) => this.start(context, cabin, passengers, preferredFareId)),
    );
  }

  private start(
    context: CheckoutContext,
    cabin: CabinClassType,
    passengers: number,
    preferredFareId: number | null,
  ) {
    this.context.set(context);
    this.cabinType.set(cabin);
    this.passengerCount.set(passengers);
    this.step.set('fare');
    const preferred = context.fares.find((f) => f.id === preferredFareId) ?? context.fares[0];
    this.fareId.set(preferred?.id ?? null);
    this.seatIds.set(Array.from({ length: passengers }, () => null));
    this.mealIds.set(Array.from({ length: passengers }, () => null));
    this.extraQuantities.set({});
    this.activePassenger.set(0);
    this.termsAccepted.set(false);
    this.buildTravellerForm(passengers);
  }

  private buildTravellerForm(passengers: number): void {
    const user = this.auth.user();
    this.travellerForm.controls.contact.reset({
      email: user?.email ?? '',
      phone: user?.phone ?? '',
    });

    const list = this.travellerForm.controls.passengers;
    list.clear();
    for (let i = 0; i < passengers; i++) {
      // Pre-fill traveller 1 with the account holder's name; they can change it.
      const [first = '', ...rest] = i === 0 && user ? user.fullName.trim().split(/\s+/) : [];
      list.push(
        this.fb.group({
          firstName: [first, [Validators.required, Validators.maxLength(50)]],
          lastName: [rest.join(' '), [Validators.required, Validators.maxLength(50)]],
          gender: ['' as Gender | '', Validators.required],
          dateOfBirth: ['', [Validators.required, pastDate]],
          nationality: ['IN', [Validators.pattern(/^[A-Za-z]{2}$/)]],
        }),
      );
    }
  }

  // ---------------- Step navigation ----------------

  /** Can the customer leave this step? Also reveals form errors when they can't. */
  validate(step: StepId): boolean {
    switch (step) {
      case 'fare':
        return this.fareId() !== null;
      case 'travellers':
        this.travellerForm.markAllAsTouched();
        return this.travellerForm.valid;
      case 'seats':
        return this.allSeatsChosen();
      case 'extras':
        return true;
      case 'review':
        return this.termsAccepted();
    }
  }

  /** Customers may jump back to any earlier step, or forward only through valid ones. */
  goTo(step: StepId): void {
    const target = STEPS.findIndex((s) => s.id === step);
    for (let i = this.stepIndex(); i < target; i++) {
      if (!this.validate(STEPS[i].id)) {
        this.step.set(STEPS[i].id);
        return;
      }
    }
    this.step.set(step);
  }

  next(): boolean {
    if (!this.validate(this.step())) {
      return false;
    }
    const nextStep = STEPS[this.stepIndex() + 1];
    if (nextStep) {
      this.step.set(nextStep.id);
    }
    return true;
  }

  back(): void {
    const previous = STEPS[this.stepIndex() - 1];
    if (previous) {
      this.step.set(previous.id);
    }
  }

  // ---------------- Seats ----------------

  passengerName(index: number): string {
    const value = this.passengerForms[index]?.getRawValue();
    const name = value ? `${value.firstName} ${value.lastName}`.trim() : '';
    return name || `Traveller ${index + 1}`;
  }

  /** Who sits in this seat? Returns the passenger index or -1. */
  occupantOf(seatId: number): number {
    return this.seatIds().indexOf(seatId);
  }

  toggleSeat(seatId: number): void {
    const active = this.activePassenger();
    const occupant = this.occupantOf(seatId);
    if (occupant === active) {
      this.setSeat(active, null); // clicking your own seat again frees it
      return;
    }
    if (occupant !== -1) {
      return; // taken by another traveller in this booking
    }
    this.setSeat(active, seatId);
    this.seatNotice.set(null);
    // Move on to the next traveller who still needs a seat.
    const nextWithoutSeat = this.seatIds().findIndex((id) => id === null);
    if (nextWithoutSeat !== -1) {
      this.activePassenger.set(nextWithoutSeat);
    }
  }

  autoAssign(): void {
    const layout = this.seatLayout();
    if (!layout) {
      return;
    }
    const ids = autoAssignSeats(layout, this.passengerCount());
    this.seatIds.set(Array.from({ length: this.passengerCount() }, (_, i) => ids[i] ?? null));
    this.seatNotice.set(
      ids.length < this.passengerCount()
        ? 'There are not enough free seats left for everyone.'
        : null,
    );
  }

  /** Re-reads live seat status, e.g. after someone else booked a seat we had chosen. */
  refreshSeats(): Observable<void> {
    const ctx = this.context();
    if (!ctx) {
      return of(undefined);
    }
    return this.seatApi.cabin(ctx.flight.id, this.cabinType()).pipe(
      map((cabin) => {
        if (!cabin) {
          return;
        }
        this.context.set({ ...ctx, cabin });
        const free = new Set(cabin.seats.filter((s) => s.status === 'AVAILABLE').map((s) => s.id));
        this.seatIds.update((ids) => ids.map((id) => (id !== null && free.has(id) ? id : null)));
        const first = this.seatIds().findIndex((id) => id === null);
        this.activePassenger.set(first === -1 ? 0 : first);
      }),
    );
  }

  private setSeat(passenger: number, seatId: number | null): void {
    this.seatIds.update((ids) => ids.map((id, i) => (i === passenger ? seatId : id)));
  }

  // ---------------- Extras ----------------

  setMeal(passenger: number, mealId: number | null): void {
    this.mealIds.update((ids) => ids.map((id, i) => (i === passenger ? mealId : id)));
  }

  setExtraQuantity(item: FlightCabinAncillary, quantity: number): void {
    const max = item.maxQuantity ?? 1;
    const clamped = Math.max(0, Math.min(max, quantity));
    this.extraQuantities.update((current) => ({ ...current, [item.id]: clamped }));
  }

  // ---------------- Booking ----------------

  /** Turns the customer's choices into the request body the API expects. */
  buildRequest(): BookingRequest {
    const ctx = this.context();
    const fareId = this.fareId();
    if (!ctx || fareId === null || !this.allSeatsChosen()) {
      throw new Error('Checkout is incomplete'); // the review step can't be reached in this state
    }
    const contact = this.travellerForm.controls.contact.getRawValue();
    const email = contact.email.trim();
    const phone = normalizePhone(contact.phone);

    return {
      flightId: ctx.flight.flightId,
      flightInstanceId: ctx.flight.id,
      cabinClass: this.cabinType(),
      fareId,
      contactInfo: { email, phone },
      passengers: this.passengerForms.map((form, i) => {
        const p = form.getRawValue();
        return {
          firstName: p.firstName.trim(),
          lastName: p.lastName.trim(),
          gender: p.gender as Gender,
          dateOfBirth: p.dateOfBirth,
          nationality: p.nationality.trim().toUpperCase() || undefined,
          // The API needs an email and phone per passenger; we use the booking contact.
          email,
          phone,
          seatInstanceId: this.seatIds()[i] as number,
        };
      }),
      mealIds: this.mealIds().filter((id): id is number => id !== null),
      // One entry per item bought: quantity 2 of extra #7 is sent as [7, 7].
      ancillaryIds: this.selectedExtras().flatMap(({ item, quantity }) =>
        Array<number>(quantity).fill(item.id),
      ),
    };
  }

  createBooking(): Observable<PaymentInitiateResponse> {
    return this.bookingApi.create(this.buildRequest());
  }
}
