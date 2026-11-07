import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <div class="container page not-found">
      <p class="not-found__code" aria-hidden="true">404</p>
      <h1>This page has flown away</h1>
      <p class="muted">The link may be old or mistyped. Let's get you back on course.</p>
      <div class="not-found__actions">
        <a routerLink="/" class="btn btn--primary">Search flights</a>
        <a routerLink="/trips" class="btn btn--outline">My trips</a>
      </div>
    </div>
  `,
  styles: `
    .not-found {
      text-align: center;
      display: grid;
      justify-items: center;
      gap: var(--space-2);
    }
    .not-found__code {
      font-size: 4rem;
      font-weight: var(--weight-bold);
      color: var(--color-accent);
      margin: 0;
      line-height: 1;
    }
    .not-found__actions {
      display: flex;
      gap: var(--space-3);
      margin-top: var(--space-3);
    }
  `,
})
export class NotFoundPage {}
