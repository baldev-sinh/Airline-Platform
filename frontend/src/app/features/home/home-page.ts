import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { SearchForm } from '../flight-search/search-form';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, SearchForm],
  templateUrl: './home-page.html',
  styleUrl: './home-page.scss',
})
export class HomePage {
  protected readonly auth = inject(AuthStore);

  /** What makes booking with us easy. Each point matches something the site really does. */
  protected readonly benefits = [
    {
      title: 'Pick your seat',
      text: 'See the cabin and choose window, aisle or a seat together, before you pay.',
      icon: 'seat',
    },
    {
      title: 'Meals and bags in one go',
      text: 'Pre-order a meal and add checked bags while you book, at the prices shown.',
      icon: 'bag',
    },
    {
      title: 'Instant e-ticket',
      text: 'Pay securely and your booking is confirmed with an e-ticket for every traveller.',
      icon: 'ticket',
    },
  ] as const;
}
