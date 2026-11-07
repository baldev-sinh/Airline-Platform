import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { FieldError, describe as describeErrors } from './field-error';

describe('FieldError messages', () => {
  it.each([
    [{ required: true }, 'Email is required'],
    [{ email: true }, 'Enter a valid email address, like name@example.com'],
    [{ minlength: { requiredLength: 10 } }, 'Use at least 10 characters'],
    [{ pattern: {} }, 'Use the format X'],
    [{ server: { message: 'Already taken' } }, 'Already taken'],
  ])('describes %o', (errors, expected) => {
    expect(describeErrors(errors, 'Email', 'Use the format X')).toBe(expected);
  });
});

@Component({
  imports: [ReactiveFormsModule, FieldError],
  template: `<input [formControl]="control" /><app-field-error
      [control]="control"
      errorId="e"
      label="Email"
    />`,
})
class HostForm {
  readonly control = new FormControl('', Validators.required);
}

describe('FieldError component', () => {
  it('shows nothing until the field is touched, then the message', async () => {
    const fixture = TestBed.createComponent(HostForm);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#e')).toBeNull();

    fixture.componentInstance.control.markAsTouched();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#e')?.textContent).toContain('Email is required');

    fixture.componentInstance.control.setValue('someone@example.com');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#e')).toBeNull();
  });
});
