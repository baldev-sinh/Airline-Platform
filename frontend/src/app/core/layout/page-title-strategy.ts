import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { BRAND } from '../config/app-config';

/**
 * Sets the browser tab title from each route's `title`, e.g. "My trips | Baldev Airways".
 * Good titles help users with many tabs, screen-reader users (it is read on navigation) and SEO.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const pageTitle = this.buildTitle(snapshot);
    this.title.setTitle(
      pageTitle ? `${pageTitle} | ${BRAND.name}` : `${BRAND.name} | Book flights`,
    );
  }
}
