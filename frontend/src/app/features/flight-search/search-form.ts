import { Component, effect, inject, input } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { CabinClassType } from '../../core/models/api-models';
import { FieldError } from '../../shared/field-error';
import { CABIN_OPTIONS } from '../../shared/labels';
import { notInPast, todayIso } from '../../shared/validators';
import { AirportField } from './airport-field';
import { MAX_PASSENGERS, SearchCriteria, toQueryParams } from './search-criteria';

/** Form-level rule: you can't fly from an airport to itself. */
function differentAirports(group: AbstractControl): ValidationErrors | null {
  const from = group.get('from')?.value as string;
  const to = group.get('to')?.value as string;
  return from && to && from === to ? { sameAirport: true } : null;
}

/**
 * The flight search box. Submitting it navigates to /flights with the search in the URL;
 * the results page reads it from there.
 */
@Component({
  selector: 'app-search-form',
  imports: [ReactiveFormsModule, AirportField, FieldError],
  templateUrl: './search-form.html',
  styleUrl: './search-form.scss',
})
export class SearchForm {
  /** Pre-fills the form, e.g. on the results page. */
  readonly initial = input<SearchCriteria | null>(null);
  /** Slimmer layout for the results page. */
  readonly compact = input(false);

  private readonly router = inject(Router);

  protected readonly cabinOptions = CABIN_OPTIONS;
  protected readonly passengerOptions = Array.from({ length: MAX_PASSENGERS }, (_, i) => i + 1);
  protected readonly today = todayIso();

  /**
   * A typed reactive form. NonNullableFormBuilder means `reset()` returns fields to their
   * initial values instead of null, and TypeScript knows every field's type.
   */
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      from: ['', Validators.required],
      to: ['', Validators.required],
      date: [this.today, [Validators.required, notInPast]],
      passengers: [1, [Validators.required, Validators.min(1), Validators.max(MAX_PASSENGERS)]],
      cabin: ['ECONOMY' as CabinClassType, Validators.required],
    },
    { validators: differentAirports },
  );

  constructor() {
    // An effect re-runs when a signal it reads changes. Here: when `initial` arrives or changes.
    effect(() => {
      const criteria = this.initial();
      if (criteria) {
        this.form.setValue({
          from: criteria.from,
          to: criteria.to,
          date: criteria.date,
          passengers: criteria.passengers,
          cabin: criteria.cabin,
        });
      }
    });
  }

  protected swap(): void {
    const { from, to } = this.form.getRawValue();
    this.form.patchValue({ from: to, to: from });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched(); // reveals every error message at once
      return;
    }
    const value = this.form.getRawValue();
    const criteria: SearchCriteria = { ...value, passengers: Number(value.passengers) };
    void this.router.navigate(['/flights'], { queryParams: toQueryParams(criteria) });
  }
}
