import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BRAND } from '../config/app-config';

@Component({
  selector: 'app-site-footer',
  imports: [RouterLink],
  template: `
    <footer class="footer no-print">
      <div class="container footer__grid">
        <div>
          <p class="footer__brand">{{ brand.name }}</p>
          <p>Fly well. Arrive better.</p>
        </div>
        <nav aria-label="Footer">
          <p class="footer__heading">Travel</p>
          <ul>
            <li><a routerLink="/">Book a flight</a></li>
            <li><a routerLink="/trips">Manage my trips</a></li>
            <li><a routerLink="/help">Baggage and help</a></li>
          </ul>
        </nav>
        <div>
          <p class="footer__heading">Customer care</p>
          <ul>
            <li>
              <a [href]="'mailto:' + brand.supportEmail">{{ brand.supportEmail }}</a>
            </li>
            <li>
              <a [href]="'tel:' + phoneHref">{{ brand.supportPhone }}</a>
            </li>
            <li>Open every day, 06:00–23:00 IST</li>
          </ul>
        </div>
      </div>
      <div class="container footer__legal">
        <p>
          © {{ year }} {{ brand.name }}. All fares are in Indian rupees and include taxes unless
          stated.
        </p>
      </div>
    </footer>
  `,
  styleUrl: './site-footer.scss',
})
export class SiteFooter {
  protected readonly brand = BRAND;
  protected readonly year = new Date().getFullYear();
  protected readonly phoneHref = BRAND.supportPhone.replace(/\s/g, '');
}
