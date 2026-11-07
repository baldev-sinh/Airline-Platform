import { Component } from '@angular/core';
import { BRAND } from '../config/app-config';

/** Each logo on the page needs its own clip-path id; SVG ids must be unique in a document. */
let nextId = 0;

/**
 * The Baldev Airways logo: an aircraft tail fin, swept back, with a saffron livery stripe
 * across it and a "B" cut into it, followed by the wordmark.
 *
 * Drawn in SVG (vector shapes), so it is sharp at every size, needs no image download, and its
 * colours come from the brand palette. public/favicon.svg is the same mark on its own.
 */
@Component({
  selector: 'app-brand-logo',
  template: `
    <svg class="mark" viewBox="0 0 48 48" width="38" height="38" aria-hidden="true">
      <defs>
        <clipPath [attr.id]="clipId">
          <path [attr.d]="fin" />
        </clipPath>
      </defs>
      <!-- The tail fin -->
      <path [attr.d]="fin" fill="#0f4c5c" />
      <!-- Saffron livery stripe, trimmed to the fin's outline -->
      <path
        [attr.clip-path]="'url(#' + clipId + ')'"
        d="M0 40 C 14 30, 30 29, 48 31 L48 37 C 30 35, 14 37, 0 47 Z"
        fill="#f29b38"
      />
      <!-- The "B", slanted to follow the fin's sweep -->
      <g
        transform="translate(22 9) skewX(-19)"
        fill="none"
        stroke="#fff"
        stroke-width="3.4"
        stroke-linejoin="round"
        stroke-linecap="square"
      >
        <path d="M0 0 V19 M0 0 H5.4 a4.3 4.3 0 0 1 0 8.6 H0 M0 8.6 H6.2 a5.2 5.2 0 0 1 0 10.4 H0" />
      </g>
    </svg>
    <span class="word"
      >{{ first }} <span class="word__rest">{{ rest }}</span></span
    >
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
    }
    .word {
      font-size: 1.3rem;
      font-weight: var(--weight-bold);
      letter-spacing: -0.02em;
      color: var(--color-brand);
      white-space: nowrap;
    }
    .word__rest {
      font-weight: var(--weight-medium);
      color: var(--color-muted);
    }
  `,
})
export class BrandLogo {
  /** Outline of the swept-back tail fin: wide at the base, narrower at the top. */
  protected readonly fin = 'M5 45 L19 5 Q19.8 3 22 3 L41 3 L34 45 Z';
  protected readonly clipId = `brand-fin-${nextId++}`;

  // "Baldev Airways" -> "Baldev" (bold) + "Airways" (lighter), as in the wordmark.
  private readonly words = BRAND.name.split(' ');
  protected readonly first = this.words[0];
  protected readonly rest = this.words.slice(1).join(' ');
}
