import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../core/auth/auth-api';
import { AuthStore } from '../../core/auth/auth-store';
import { errorMessage } from '../../core/http/api-error';
import { UserRole } from '../../core/models/api-models';
import { ErrorState, Loading } from '../../shared/async-state';

const ROLE_LABELS: Record<UserRole, string> = {
  ROLE_USER: 'Traveller',
  ROLE_AIRLINE_OWNER: 'Airline partner',
  ROLE_SYSTEM_ADMIN: 'Administrator',
};

@Component({
  selector: 'app-account-page',
  imports: [DatePipe, RouterLink, Loading, ErrorState],
  template: `
    <div class="container page">
      <header class="page-header">
        <h1>My account</h1>
        <p>Your profile details.</p>
      </header>

      @if (profile.isLoading()) {
        <app-loading label="Loading your profile" />
      } @else if (profile.error()) {
        <app-error-state [message]="errorText()" (retry)="profile.reload()" />
      } @else if (profile.hasValue()) {
        @let user = profile.value();
        <section class="card account" aria-labelledby="profile-title">
          <h2 id="profile-title" class="card__title">Profile</h2>
          <dl class="facts">
            <dt>Name</dt>
            <dd>{{ user.fullName }}</dd>
            <dt>Email</dt>
            <dd>{{ user.email }}</dd>
            <dt>Mobile</dt>
            <dd>{{ user.phone || 'Not added' }}</dd>
            <dt>Account type</dt>
            <dd>{{ roleLabels[user.role] }}</dd>
            @if (user.lastLogin) {
              <dt>Last sign-in</dt>
              <dd>{{ user.lastLogin | date: 'd MMM y, HH:mm' }}</dd>
            }
          </dl>
          <hr class="divider" />
          <div class="account__actions">
            <a routerLink="/trips" class="btn btn--brand">My trips</a>
            <button type="button" class="btn btn--outline" (click)="signOut()">Sign out</button>
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .account {
      max-width: 40rem;
    }
    .account__actions {
      display: flex;
      gap: var(--space-3);
      flex-wrap: wrap;
    }
  `,
})
export class AccountPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApi);

  /** Fetched fresh (not only from the stored session) so it reflects the latest data. */
  protected readonly profile = rxResource({ stream: () => this.authApi.profile() });
  protected readonly errorText = computed(() => errorMessage(this.profile.error()));
  protected readonly roleLabels = ROLE_LABELS;

  protected signOut(): void {
    this.auth.logout();
    void this.router.navigate(['/']);
  }
}
