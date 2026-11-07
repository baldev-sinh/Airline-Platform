import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthStore } from '../auth/auth-store';
import { BrandLogo } from './brand-logo';

@Component({
  selector: 'app-site-header',
  imports: [RouterLink, RouterLinkActive, BrandLogo],
  templateUrl: './site-header.html',
  styleUrl: './site-header.scss',
})
export class SiteHeader {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Mobile menu open/closed. On wide screens the menu is always visible (see the .scss). */
  protected readonly menuOpen = signal(false);

  constructor() {
    // Close the mobile menu after navigating. takeUntilDestroyed() unsubscribes automatically
    // when this component is destroyed, which prevents memory leaks.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected signOut(): void {
    this.auth.logout();
    void this.router.navigate(['/']);
  }

  protected firstName(fullName: string): string {
    return fullName.trim().split(/\s+/)[0] ?? fullName;
  }
}
