import { Component, computed, inject } from '@angular/core';
import { FlightCabinAncillary } from '../../../core/models/api-models';
import { MoneyPipe } from '../../../shared/money-pipe';
import { CheckoutStore } from '../checkout-store';

@Component({
  selector: 'app-extras-step',
  imports: [MoneyPipe],
  templateUrl: './extras-step.html',
  styleUrl: './extras-step.scss',
})
export class ExtrasStep {
  protected readonly store = inject(CheckoutStore);
  protected readonly meals = computed(() => this.store.context()?.meals ?? []);
  protected readonly extras = computed(() => this.store.context()?.extras ?? []);
  protected readonly travellers = computed(() =>
    Array.from({ length: this.store.passengerCount() }, (_, i) => ({
      index: i,
      name: this.store.passengerName(i),
    })),
  );
  protected readonly mealsIncluded = computed(
    () => !!this.store.selectedFare()?.complimentaryMeals,
  );

  protected quantity(item: FlightCabinAncillary): number {
    return this.store.extraQuantities()[item.id] ?? 0;
  }

  protected max(item: FlightCabinAncillary): number {
    return item.maxQuantity ?? 1;
  }

  protected onMealChange(passenger: number, value: string): void {
    this.store.setMeal(passenger, value === '' ? null : Number(value));
  }
}
