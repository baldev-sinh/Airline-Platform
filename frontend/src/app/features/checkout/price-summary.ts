import { Component, inject } from '@angular/core';
import { MoneyPipe } from '../../shared/money-pipe';
import { CheckoutStore } from './checkout-store';

/** The running total, always visible beside the steps. Reads straight from the store's signals. */
@Component({
  selector: 'app-price-summary',
  imports: [MoneyPipe],
  template: `
    @let p = store.price();
    <section class="card summary">
      <h2 class="card__title">Your price</h2>
      <dl class="summary__lines">
        <div>
          <dt>
            {{ store.selectedFare()?.name ?? 'Fare' }}
            <span class="muted">× {{ store.passengerCount() }}</span>
          </dt>
          <dd>{{ p.fare | money }}</dd>
        </div>
        @if (p.seats > 0) {
          <div>
            <dt>Seats</dt>
            <dd>{{ p.seats | money }}</dd>
          </div>
        }
        @if (p.meals > 0) {
          <div>
            <dt>Meals</dt>
            <dd>{{ p.meals | money }}</dd>
          </div>
        }
        @if (p.extras > 0) {
          <div>
            <dt>Extra bags and services</dt>
            <dd>{{ p.extras | money }}</dd>
          </div>
        }
      </dl>
      <div class="summary__total" aria-live="polite">
        <span>Total</span>
        <strong>{{ p.total | money }}</strong>
      </div>
      <p class="summary__note">
        Includes taxes and fees. The final amount is confirmed before you pay.
      </p>
    </section>
  `,
  styles: `
    .summary__lines {
      display: grid;
      gap: var(--space-2);
      margin: 0;
    }
    .summary__lines div {
      display: flex;
      justify-content: space-between;
      gap: var(--space-3);
    }
    dd {
      margin: 0;
      font-variant-numeric: tabular-nums;
    }
    .summary__total {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      margin-top: var(--space-4);
      padding-top: var(--space-3);
      border-top: 1px solid var(--color-line);
      font-size: var(--text-lg);
      font-weight: var(--weight-semibold);
    }
    .summary__total strong {
      font-size: var(--text-2xl);
      color: var(--color-brand);
    }
    .summary__note {
      margin: var(--space-2) 0 0;
      font-size: var(--text-xs);
      color: var(--color-muted);
    }
  `,
})
export class PriceSummary {
  protected readonly store = inject(CheckoutStore);
}
