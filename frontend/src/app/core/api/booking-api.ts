import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  Booking,
  BookingRequest,
  Payment,
  PaymentInitiateResponse,
  PaymentVerifyRequest,
} from '../models/api-models';

@Injectable({ providedIn: 'root' })
export class BookingApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * Creates a PENDING booking, holds the seats for 20 minutes and opens a payment.
   * The response contains the `checkoutUrl` to send the customer to.
   */
  create(request: BookingRequest): Observable<PaymentInitiateResponse> {
    return this.http.post<PaymentInitiateResponse>(`${this.baseUrl}/api/bookings`, request);
  }

  get(id: number): Observable<Booking> {
    return this.http.get<Booking>(`${this.baseUrl}/api/bookings/${id}`);
  }

  /** All bookings of the signed-in customer, newest first. */
  myBookings(): Observable<Booking[]> {
    return this.http
      .get<Booking[]>(`${this.baseUrl}/api/bookings/user/history`)
      .pipe(map((list) => [...list].sort((a, b) => b.bookingDate.localeCompare(a.bookingDate))));
  }

  /** Only unpaid (PENDING) bookings can be cancelled online; it releases the held seats. */
  cancel(id: number): Observable<Booking> {
    return this.http.patch<Booking>(`${this.baseUrl}/api/bookings/${id}/cancel`, null);
  }

  /**
   * Called when the customer returns from the payment page. The server re-checks the payment with
   * the provider before confirming anything, so this is safe even if someone edits the URL.
   */
  verifyPayment(request: PaymentVerifyRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/api/payments/verify`, request);
  }
}
