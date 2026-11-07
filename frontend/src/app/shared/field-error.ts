import { Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, ValidationErrors } from '@angular/forms';
import { startWith, switchMap } from 'rxjs';

/**
 * Shows the validation message of one form control, once the user has touched it
 * (or tried to submit, which marks every control as touched).
 *
 *   <input id="email" formControlName="email" aria-describedby="email-error" />
 *   <app-field-error [control]="form.controls.email" errorId="email-error" label="Email" />
 *
 * Why the `events` stream? Components here use OnPush change detection (Angular's default
 * since v22): they only re-render when a signal or input they use changes. A form control
 * changing its errors is neither, so we turn the control's `events` stream into a signal
 * and the message updates exactly when the control does.
 */
@Component({
  selector: 'app-field-error',
  template: `
    @if (message(); as text) {
      <p class="field__error" [id]="errorId()">{{ text }}</p>
    }
  `,
})
export class FieldError {
  readonly control = input.required<AbstractControl>();
  /** Put the same id in the input's aria-describedby so screen readers read the error. */
  readonly errorId = input.required<string>();
  /** Used in messages: "Email is required". */
  readonly label = input('This field');
  /** Message shown for a failed `Validators.pattern`. */
  readonly patternMessage = input('Please check the format');

  private readonly changes = toSignal(
    toObservable(this.control).pipe(switchMap((control) => control.events.pipe(startWith(null)))),
  );

  protected readonly message = computed(() => {
    this.changes(); // re-run whenever the control emits an event
    const control = this.control();
    if (!control.touched || !control.errors) {
      return null;
    }
    return describe(control.errors, this.label(), this.patternMessage());
  });
}

/** Turns Angular's error object into one sentence. The first matching rule wins. */
export function describe(errors: ValidationErrors, label: string, patternMessage: string): string {
  if (errors['required']) return `${label} is required`;
  if (errors['email']) return 'Enter a valid email address, like name@example.com';
  if (errors['minlength']) return `Use at least ${errors['minlength'].requiredLength} characters`;
  if (errors['maxlength']) return `Use at most ${errors['maxlength'].requiredLength} characters`;
  if (errors['min']) return `Must be at least ${errors['min'].min}`;
  if (errors['max']) return `Must be at most ${errors['max'].max}`;
  if (errors['pattern']) return patternMessage;
  // Custom validators in this app return { someKey: { message: '...' } }.
  for (const value of Object.values(errors)) {
    if (value && typeof value === 'object' && typeof value.message === 'string') {
      return value.message;
    }
  }
  return `${label} is not valid`;
}
