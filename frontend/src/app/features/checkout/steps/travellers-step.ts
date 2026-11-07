import { Component, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { FieldError } from '../../../shared/field-error';
import { GENDER_LABELS } from '../../../shared/labels';
import { PHONE_HINT, todayIso } from '../../../shared/validators';
import { CheckoutStore } from '../checkout-store';

@Component({
  selector: 'app-travellers-step',
  imports: [ReactiveFormsModule, FieldError],
  templateUrl: './travellers-step.html',
  styles: `
    .traveller {
      margin-bottom: var(--space-4);
    }
    .traveller legend {
      font-size: var(--text-lg);
      font-weight: var(--weight-bold);
      margin-bottom: var(--space-4);
    }
    .traveller__tag {
      font-weight: var(--weight-regular);
      color: var(--color-muted);
      font-size: var(--text-sm);
    }
  `,
})
export class TravellersStep {
  protected readonly store = inject(CheckoutStore);
  protected readonly form = this.store.travellerForm;
  protected readonly genders = Object.entries(GENDER_LABELS);
  protected readonly phoneHint = PHONE_HINT;
  protected readonly today = todayIso();
}
