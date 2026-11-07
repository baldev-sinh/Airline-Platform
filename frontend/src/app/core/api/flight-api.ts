import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { CabinClassType, Fare, FlightInstance, LocalDateString } from '../models/api-models';
import { Page, SpringPage, toPage } from '../models/page';

/** What the customer searched for, in the form the API expects (airport ids, not codes). */
export interface FlightSearchQuery {
  readonly fromAirportId: number;
  readonly toAirportId: number;
  readonly date: LocalDateString;
  readonly passengers: number;
  readonly cabin: CabinClassType;
  /** Server-side ordering. Supported by the API: departure (default), arrival, duration. */
  readonly sortBy?: 'departure' | 'arrival' | 'duration';
}

/**
 * One route on one day is a short list, so we fetch it in a single page and filter it in the
 * browser. That makes filters instant and avoids a request on every click.
 */
export const SEARCH_PAGE_SIZE = 100;

@Injectable({ providedIn: 'root' })
export class FlightApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /** Public: works without signing in. */
  search(query: FlightSearchQuery): Observable<Page<FlightInstance>> {
    const params = new HttpParams({
      fromObject: {
        departureAirportId: query.fromAirportId,
        arrivalAirportId: query.toAirportId,
        departureDate: query.date,
        passengers: query.passengers,
        cabinClass: query.cabin,
        sortBy: query.sortBy ?? 'departure',
        sortOrder: 'asc',
        page: 0,
        size: SEARCH_PAGE_SIZE,
      },
    });
    return this.http
      .get<SpringPage<FlightInstance>>(`${this.baseUrl}/api/flights/search`, { params })
      .pipe(map(toPage));
  }

  /** One dated flight. Requires sign-in. */
  flightInstance(id: number): Observable<FlightInstance> {
    return this.http.get<FlightInstance>(`${this.baseUrl}/api/flight-instances/${id}`);
  }

  /** All fares (Saver, Flexi...) for a flight in one cabin, cheapest first. Requires sign-in. */
  fares(flightId: number, cabinClassId: number): Observable<Fare[]> {
    return this.http
      .get<Fare[]>(`${this.baseUrl}/api/fares/flight/${flightId}/cabin-class/${cabinClassId}`)
      .pipe(map((fares) => [...fares].sort((a, b) => a.totalPrice - b.totalPrice)));
  }
}
