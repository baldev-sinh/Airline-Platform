import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BRAND } from '../../core/config/app-config';

/**
 * Static help content. Every statement here matches how the booking system actually behaves
 * (seat hold time, payment window, what can be cancelled online), so customers aren't misled.
 */
@Component({
  selector: 'app-help-page',
  imports: [RouterLink],
  templateUrl: './help-page.html',
  styles: `
    .help {
      display: grid;
      gap: var(--space-4);
      max-width: 48rem;
    }
    details {
      background: var(--color-surface);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-lg);
    }
    summary {
      cursor: pointer;
      padding: var(--space-4);
      font-weight: var(--weight-semibold);
      font-size: var(--text-lg);
    }
    details > div {
      padding: 0 var(--space-4) var(--space-4);
    }
    details[open] summary {
      border-bottom: 1px solid var(--color-line);
      margin-bottom: var(--space-3);
    }
    ul {
      padding-left: var(--space-5);
      margin: 0 0 var(--space-3);
    }
  `,
})
export class HelpPage {
  protected readonly brand = BRAND;
}
