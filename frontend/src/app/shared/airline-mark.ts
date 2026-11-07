import { Component, computed, input, signal } from '@angular/core';

/**
 * The airline's logo, or a neat two-letter tile when there is no logo (or it fails to load).
 *   <app-airline-mark name="Skyline Airways" [logoUrl]="flight.airlineLogo" />
 */
@Component({
  selector: 'app-airline-mark',
  template: `
    @if (logoUrl() && !logoFailed()) {
      <img
        [src]="logoUrl()"
        alt=""
        width="36"
        height="36"
        loading="lazy"
        (error)="logoFailed.set(true)"
      />
    } @else {
      <span class="tile" aria-hidden="true">{{ initials() }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-shrink: 0;
    }
    img,
    .tile {
      width: 2.25rem;
      height: 2.25rem;
      border-radius: var(--radius-md);
    }
    img {
      object-fit: contain;
      background: #fff;
      border: 1px solid var(--color-line);
    }
    .tile {
      display: grid;
      place-items: center;
      background: var(--color-brand-soft);
      color: var(--color-brand);
      font-weight: var(--weight-bold);
      font-size: var(--text-sm);
    }
  `,
})
export class AirlineMark {
  readonly name = input.required<string>();
  readonly logoUrl = input<string | null>(null);

  protected readonly logoFailed = signal(false);
  protected readonly initials = computed(() =>
    this.name()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join(''),
  );
}
