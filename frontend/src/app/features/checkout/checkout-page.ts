import { DatePipe } from '@angular/common';
import {
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http/api-error';
import { CabinClassType, FlightStatus } from '../../core/models/api-models';
import { ErrorState, Loading } from '../../shared/async-state';
import { CABIN_LABELS } from '../../shared/labels';
import { MAX_PASSENGERS } from '../flight-search/search-criteria';
import { CheckoutStore, STEPS, StepId } from './checkout-store';
import { PriceSummary } from './price-summary';
import { ExtrasStep } from './steps/extras-step';
import { FareStep } from './steps/fare-step';
import { ReviewStep } from './steps/review-step';
import { SeatStep } from './steps/seat-step';
import { TravellersStep } from './steps/travellers-step';

/** Flights in these states can't be sold any more. */
const CLOSED_STATUSES: readonly FlightStatus[] = [
  'BOARDING',
  'DEPARTED',
  'IN_AIR',
  'LANDED',
  'ARRIVED',
  'CANCELLED',
  'DIVERTED',
  'COMPLETED',
];

/**
 * /book/:flightInstanceId?cabin=ECONOMY&pax=2&fare=17
 *
 * The checkout is one page with five steps (not five routes): the choices belong together,
 * and nothing is saved on the server until the customer pays. The page owns a CheckoutStore
 * (see `providers`) that every step shares.
 */
@Component({
  selector: 'app-checkout-page',
  imports: [
    DatePipe,
    RouterLink,
    Loading,
    ErrorState,
    PriceSummary,
    FareStep,
    TravellersStep,
    SeatStep,
    ExtrasStep,
    ReviewStep,
  ],
  providers: [CheckoutStore],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.scss',
})
export class CheckoutPage {
  readonly flightInstanceId = input.required<string>();
  readonly cabin = input<string>();
  readonly pax = input<string>();
  readonly fare = input<string>();

  protected readonly store = inject(CheckoutStore);
  private readonly injector = inject(Injector);

  protected readonly steps = STEPS;
  protected readonly cabinLabels = CABIN_LABELS;
  protected readonly stepError = signal<string | null>(null);

  /** URL values are untrusted text: turn them into safe, typed values with sensible defaults. */
  private readonly request = computed(() => {
    const id = Number(this.flightInstanceId());
    const cabin = (this.cabin() ?? 'ECONOMY').toUpperCase();
    const pax = Number(this.pax() ?? '1');
    const fareId = Number(this.fare());
    return {
      id: Number.isInteger(id) && id > 0 ? id : -1,
      cabin: (cabin in CABIN_LABELS ? cabin : 'ECONOMY') as CabinClassType,
      passengers: Number.isInteger(pax) && pax >= 1 && pax <= MAX_PASSENGERS ? pax : 1,
      fareId: Number.isInteger(fareId) && fareId > 0 ? fareId : null,
    };
  });

  protected readonly context = rxResource({
    params: () => this.request(),
    stream: ({ params }) =>
      this.store.load(params.id, params.cabin, params.passengers, params.fareId),
  });

  protected readonly errorText = computed(() => errorMessage(this.context.error()));

  /** A reason this flight can't be booked, or null if it can. */
  protected readonly blocker = computed(() => {
    const ctx = this.store.context();
    if (!ctx) {
      return null;
    }
    if (CLOSED_STATUSES.includes(ctx.flight.status)) {
      return 'This flight is no longer open for booking.';
    }
    if (ctx.cabin.canBook === false || ctx.fares.length === 0) {
      return 'This cabin is not on sale for this flight.';
    }
    const free = ctx.cabin.seats.filter((s) => s.status === 'AVAILABLE').length;
    if (free < this.store.passengerCount()) {
      return `Only ${free} ${free === 1 ? 'seat is' : 'seats are'} left in this cabin, not enough for your group.`;
    }
    return null;
  });

  protected continue(): void {
    const step = this.store.step();
    if (!this.store.next()) {
      this.stepError.set(this.messageFor(step));
      if (step === 'travellers') {
        this.focus('.checkout__body input.ng-invalid, .checkout__body select.ng-invalid');
      }
      return;
    }
    this.stepChanged();
  }

  protected back(): void {
    this.store.back();
    this.stepChanged();
  }

  protected goTo(step: StepId): void {
    this.store.goTo(step);
    this.stepChanged();
  }

  /** Called by the review step when the server rejected the seats (someone else was faster). */
  protected onSeatsLost(): void {
    this.store.step.set('seats');
    this.stepChanged();
  }

  private stepChanged(): void {
    this.stepError.set(null);
    window.scrollTo({ top: 0 });
    // Move keyboard/screen-reader focus to the new step's heading once it has rendered.
    this.focus('#step-heading');
  }

  private focus(selector: string): void {
    afterNextRender(() => document.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }

  private messageFor(step: StepId): string {
    switch (step) {
      case 'fare':
        return 'Please choose a fare to continue.';
      case 'travellers':
        return 'Please check the highlighted traveller details.';
      case 'seats':
        return 'Please choose a seat for every traveller, or use "Choose seats for me".';
      default:
        return 'Please complete this step to continue.';
    }
  }
}
