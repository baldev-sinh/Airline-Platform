import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';
import { formatCurrency, getCurrencySymbol } from '@angular/common';
import { BRAND } from '../core/config/app-config';

/**
 * Formats an amount as money for the user's locale:  {{ 5300 | money }}  ->  "₹5,300"
 *                                                     {{ 5300.5 | money }} -> "₹5,300.50"
 *                                                     {{ 40 | money: 'USD' }} -> "$40"
 *
 * Whole amounts are shown without ".00", the way airlines show fares. A pure pipe (the default)
 * only re-runs when its input changes, so it is cheap to use in long lists.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(amount: number | null | undefined, currency?: string | null): string {
    if (amount === null || amount === undefined || Number.isNaN(amount)) {
      return '–';
    }
    const code = currency || BRAND.defaultCurrency;
    const digits = Number.isInteger(amount) ? '1.0-0' : '1.2-2';
    return formatCurrency(
      amount,
      this.locale,
      getCurrencySymbol(code, 'narrow', this.locale),
      code,
      digits,
    );
  }
}
