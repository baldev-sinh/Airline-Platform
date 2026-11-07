import { Component, inject } from '@angular/core';
import { Fare } from '../../../core/models/api-models';
import { MoneyPipe } from '../../../shared/money-pipe';
import { CheckoutStore } from '../checkout-store';

export interface FareFeature {
  readonly text: string;
  readonly included: boolean;
}

/** Turns a fare's many yes/no fields into a short, comparable list of what you get. */
export function fareFeatures(fare: Fare): FareFeature[] {
  const bag = fare.baggagePolicy;
  const rules = fare.fareRules;
  const features: FareFeature[] = [];

  if (bag?.cabinBaggageMaxWeight) {
    features.push({ text: `Cabin bag up to ${bag.cabinBaggageMaxWeight} kg`, included: true });
  }
  features.push(
    bag?.checkInBaggageMaxWeight
      ? { text: `Checked bag ${bag.checkInBaggageMaxWeight} kg`, included: true }
      : { text: 'No checked bag (add one later)', included: false },
  );
  features.push({
    text: fare.complimentaryMeals ? 'Meal included' : 'Meals to buy',
    included: !!fare.complimentaryMeals,
  });

  if (fare.freeDateChange) {
    features.push({ text: 'Free date change', included: true });
  } else if (rules?.isChangeable) {
    features.push({
      text: rules.changeFee ? 'Date change for a fee' : 'Date change allowed',
      included: true,
    });
  } else {
    features.push({ text: 'No date changes', included: false });
  }

  if (fare.fullRefund) {
    features.push({ text: 'Fully refundable', included: true });
  } else if (fare.partialRefund || rules?.isRefundable) {
    features.push({ text: 'Partly refundable', included: true });
  } else {
    features.push({ text: 'Non-refundable', included: false });
  }

  if (fare.priorityBoarding) features.push({ text: 'Priority boarding', included: true });
  if (fare.loungeAccess) features.push({ text: 'Lounge access', included: true });
  if (fare.extraSeatSpace) features.push({ text: 'Extra legroom', included: true });
  return features;
}

@Component({
  selector: 'app-fare-step',
  imports: [MoneyPipe],
  template: `
    <h2 id="step-heading" tabindex="-1">Choose your fare</h2>
    <p class="muted">Prices are per adult and include taxes and fees.</p>

    <!-- A real radio group: arrow keys move between fares, and screen readers say "1 of 3". -->
    <fieldset class="plain">
      <legend class="visually-hidden">Fare</legend>
      <div class="fares">
        @for (fare of store.context()?.fares; track fare.id) {
          <label class="fare" [class.is-selected]="store.fareId() === fare.id">
            <input
              type="radio"
              name="fare"
              class="visually-hidden"
              [checked]="store.fareId() === fare.id"
              (change)="store.fareId.set(fare.id)"
            />
            <span class="fare__head">
              <span class="fare__name">{{ fare.name }}</span>
              @if (fare.fareLabel) {
                <span class="badge">{{ fare.fareLabel }}</span>
              }
            </span>
            <span class="fare__price">{{ fare.totalPrice | money }}</span>
            <ul class="fare__features">
              @for (feature of features(fare); track feature.text) {
                <li [class.is-missing]="!feature.included">
                  <span aria-hidden="true">{{ feature.included ? '✓' : '–' }}</span>
                  {{ feature.text }}
                </li>
              }
            </ul>
            <span class="fare__pick" aria-hidden="true">{{
              store.fareId() === fare.id ? 'Selected' : 'Select'
            }}</span>
          </label>
        }
      </div>
    </fieldset>
  `,
  styleUrl: './fare-step.scss',
})
export class FareStep {
  protected readonly store = inject(CheckoutStore);
  protected readonly features = fareFeatures;
}
