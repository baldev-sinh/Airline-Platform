import { Component, computed, forwardRef, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { AirportDirectory, cityName, filterAirports } from '../../core/api/airport-directory';
import { Airport } from '../../core/models/api-models';

/**
 * Airport picker with type-ahead ("mum" -> Mumbai, BOM).
 *
 * It follows the WAI-ARIA "combobox" pattern, so it works with a keyboard and screen readers:
 *   - Arrow Down / Up move through suggestions, Enter picks, Escape closes.
 *   - The input says which suggestion is highlighted via aria-activedescendant.
 *
 * It implements ControlValueAccessor, the interface that lets a custom component work with
 * Angular forms exactly like a native <input>:  <app-airport-field formControlName="from" />
 * The form value is the airport CODE ("BOM").
 */
@Component({
  selector: 'app-airport-field',
  templateUrl: './airport-field.html',
  styleUrl: './airport-field.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AirportField), multi: true },
  ],
})
export class AirportField implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly label = input.required<string>();
  readonly placeholder = input('City or airport');
  /** id of an error message element, linked for screen readers. */
  readonly describedBy = input<string | null>(null);
  readonly invalid = input(false);

  private readonly airports = toSignal(inject(AirportDirectory).airports$, {
    initialValue: [] as readonly Airport[],
  });

  /** The chosen airport code: the form value. */
  private readonly code = signal<string | null>(null);
  /** What is typed in the box. */
  protected readonly text = signal('');
  protected readonly open = signal(false);
  protected readonly activeIndex = signal(0);
  protected readonly disabled = signal(false);

  protected readonly selected = computed(
    () => this.airports().find((a) => a.iataCode === this.code()) ?? null,
  );
  protected readonly suggestions = computed(() => filterAirports(this.airports(), this.text()));
  /** Text in the box: what the user is typing while open, otherwise the selected airport. */
  protected readonly displayText = computed(() => {
    const airport = this.selected();
    return this.open() || !airport ? this.text() : airportLabel(airport);
  });
  protected readonly listId = computed(() => `${this.inputId()}-list`);
  protected readonly activeOptionId = computed(() =>
    this.open() && this.suggestions().length > 0
      ? `${this.inputId()}-opt-${this.activeIndex()}`
      : null,
  );

  private onChange: (code: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  // ----- ControlValueAccessor: Angular forms call these -----

  writeValue(code: string | null): void {
    this.code.set(code || null);
    this.text.set(''); // shows the selected airport's label instead
  }

  registerOnChange(fn: (code: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  // ----- Template event handlers -----

  protected onFocus(): void {
    this.text.set('');
    this.activeIndex.set(0);
    this.open.set(true);
  }

  protected onInput(value: string): void {
    this.text.set(value);
    this.activeIndex.set(0);
    this.open.set(true);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const count = this.suggestions().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.open.set(true);
        this.activeIndex.update((i) => (count === 0 ? 0 : (i + 1) % count));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.update((i) => (count === 0 ? 0 : (i - 1 + count) % count));
        break;
      case 'Enter':
        if (this.open() && count > 0) {
          event.preventDefault(); // don't submit the form while picking
          this.choose(this.suggestions()[this.activeIndex()]);
        }
        break;
      case 'Escape':
        this.open.set(false);
        break;
    }
  }

  protected onBlur(): void {
    // If they typed something and left, pick the best match, like most airline sites do.
    const typed = this.text().trim();
    if (this.open() && typed !== '' && this.suggestions().length > 0) {
      this.choose(this.suggestions()[0]);
    }
    this.open.set(false);
    this.onTouched();
  }

  /** mousedown (not click) fires before the input's blur, so the choice isn't lost. */
  protected onOptionMousedown(event: MouseEvent, airport: Airport): void {
    event.preventDefault();
    this.choose(airport);
  }

  private choose(airport: Airport): void {
    this.code.set(airport.iataCode);
    this.text.set('');
    this.open.set(false);
    this.onChange(airport.iataCode);
  }

  protected readonly cityName = cityName;
}

export function airportLabel(airport: Airport): string {
  return `${cityName(airport)} (${airport.iataCode})`;
}
