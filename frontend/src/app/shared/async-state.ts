import { Component, input, output } from '@angular/core';

/**
 * Three small building blocks every data-driven page needs, so loading and error screens look
 * and behave the same everywhere:
 *
 *   <app-loading label="Finding flights" />
 *   <app-error-state [message]="..." (retry)="resource.reload()" />
 *   <app-empty-state title="No trips yet">Optional extra content</app-empty-state>
 */

@Component({
  selector: 'app-loading',
  // role="status" makes screen readers announce the label without moving focus.
  template: `
    <div class="loading" role="status">
      <span class="loading__bar" aria-hidden="true"></span>
      <span>{{ label() }}…</span>
    </div>
  `,
  styles: `
    .loading {
      display: grid;
      justify-items: center;
      gap: var(--space-3);
      padding: var(--space-7) var(--space-4);
      color: var(--color-muted);
    }
    .loading__bar {
      width: 10rem;
      height: 3px;
      border-radius: 3px;
      background: linear-gradient(90deg, transparent, var(--color-accent), transparent)
        var(--color-line);
      background-size: 50% 100%;
      background-repeat: no-repeat;
      animation: slide 1.1s ease-in-out infinite;
    }
    @keyframes slide {
      from {
        background-position: -100% 0;
      }
      to {
        background-position: 200% 0;
      }
    }
  `,
})
export class Loading {
  readonly label = input('Loading');
}

@Component({
  selector: 'app-error-state',
  template: `
    <div class="alert alert--danger" role="alert">
      <div>
        <p>
          <strong>{{ title() }}</strong>
        </p>
        <p>{{ message() }}</p>
        @if (retryable()) {
          <button type="button" class="btn btn--outline retry" (click)="retry.emit()">
            Try again
          </button>
        }
      </div>
    </div>
  `,
  styles: `
    .retry {
      margin-top: var(--space-3);
    }
  `,
})
export class ErrorState {
  readonly title = input("We couldn't load this");
  readonly message = input.required<string>();
  readonly retryable = input(true);
  readonly retry = output<void>();
}

@Component({
  selector: 'app-empty-state',
  template: `
    <div class="empty card">
      <svg width="56" height="56" viewBox="0 0 56 56" aria-hidden="true">
        <circle cx="28" cy="28" r="27" fill="var(--color-brand-soft)" />
        <path d="M14 31l28-10-6 16-7-4-4 5-1-7z" fill="var(--color-brand)" />
      </svg>
      <h2>{{ title() }}</h2>
      <ng-content />
    </div>
  `,
  styles: `
    .empty {
      display: grid;
      justify-items: center;
      text-align: center;
      gap: var(--space-2);
      padding: var(--space-7) var(--space-4);
    }
    h2 {
      margin: var(--space-2) 0 0;
    }
  `,
})
export class EmptyState {
  readonly title = input.required<string>();
}
