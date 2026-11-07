import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { FlightInstance } from '../../core/models/api-models';
import { AirlineMark } from '../../shared/airline-mark';
import { MoneyPipe } from '../../shared/money-pipe';
import { dayOffset } from './flight-filters';

/** Few seats left: show urgency, honestly (the number comes from live inventory). */
const LOW_SEATS = 9;

/**
 * One flight in the results list. "Presentational" component: it receives data through inputs
 * and reports clicks through an output. It never calls the API itself, which keeps it reusable
 * and easy to test.
 */
@Component({
  selector: 'app-flight-card',
  imports: [DatePipe, MoneyPipe, AirlineMark],
  templateUrl: './flight-card.html',
  styleUrl: './flight-card.scss',
})
export class FlightCard {
  readonly flight = input.required<FlightInstance>();
  readonly passengers = input(1);
  readonly selected = output<FlightInstance>();

  protected readonly arrivesNextDay = computed(() =>
    dayOffset(this.flight().departureDateTime, this.flight().arrivalDateTime),
  );
  protected readonly seatsLeft = computed(() => {
    const seats = this.flight().availableSeats;
    return seats > 0 && seats <= LOW_SEATS ? seats : null;
  });
  protected readonly totalForAll = computed(() => {
    const perPerson = this.flight().fare?.totalPrice;
    return perPerson === undefined ? null : perPerson * this.passengers();
  });
}
