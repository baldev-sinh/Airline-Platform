import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { FlightCabinAncillary, FlightMeal } from '../models/api-models';

/** Meals and other extras (bags, travel protection) that can be added to a booking. */
@Injectable({ providedIn: 'root' })
export class ExtrasApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /** Meals on sale for this flight (unavailable ones are filtered out). */
  meals(flightId: number): Observable<FlightMeal[]> {
    return this.http
      .get<FlightMeal[]>(`${this.baseUrl}/api/flight-meals/flight/${flightId}`)
      .pipe(map((meals) => meals.filter((m) => m.available !== false)));
  }

  /** Extras for this flight and cabin that the customer can buy (not ones already in the fare). */
  extras(flightId: number, cabinClassId: number): Observable<FlightCabinAncillary[]> {
    return this.http
      .get<FlightCabinAncillary[]>(
        `${this.baseUrl}/api/flight-cabin-ancillaries/flight/${flightId}/cabin/${cabinClassId}`,
      )
      .pipe(
        map((items) => items.filter((i) => i.available !== false && i.includedInFare !== true)),
      );
  }
}
