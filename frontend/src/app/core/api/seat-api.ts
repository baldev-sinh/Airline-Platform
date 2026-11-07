import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { CabinClassType, FlightInstanceCabin } from '../models/api-models';
import { SpringPage, toPage } from '../models/page';

@Injectable({ providedIn: 'root' })
export class SeatApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * The cabin (with every seat and its live status) of one dated flight.
   * Emits `null` if the aircraft has no such cabin.
   */
  cabin(flightInstanceId: number, cabin: CabinClassType): Observable<FlightInstanceCabin | null> {
    return this.http
      .get<SpringPage<FlightInstanceCabin>>(
        `${this.baseUrl}/api/flight-instance-cabins/flight-instance/${flightInstanceId}`,
        { params: { size: 10 } },
      )
      .pipe(map((raw) => toPage(raw).items.find((c) => c.cabinClassType === cabin) ?? null));
  }
}
