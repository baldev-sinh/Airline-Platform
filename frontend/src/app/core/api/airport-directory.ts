import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { Airport } from '../models/api-models';

/**
 * The list of airports we fly to. It rarely changes, so it is downloaded once per visit and shared
 * by every page that needs it (search form, results page...).
 *
 * `shareReplay(1)` keeps the last response and gives it to later subscribers without a new
 * request. If the request FAILS, nothing is cached, so the next subscriber tries again.
 */
@Injectable({ providedIn: 'root' })
export class AirportDirectory {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  readonly airports$: Observable<readonly Airport[]> = this.http
    .get<Airport[]>(`${this.baseUrl}/api/airports`)
    .pipe(
      map((airports) => [...airports].sort((a, b) => cityName(a).localeCompare(cityName(b)))),
      shareReplay(1),
    );

  findByCode(code: string | null | undefined): Observable<Airport | undefined> {
    const wanted = (code ?? '').trim().toUpperCase();
    return this.airports$.pipe(
      map((list) => list.find((a) => a.iataCode.toUpperCase() === wanted)),
    );
  }
}

export function cityName(airport: Airport): string {
  return airport.city?.name ?? airport.name;
}

/**
 * Case-insensitive match on city, airport name, IATA code or country, e.g. "mum", "BOM", "india".
 * Exact code matches come first, so typing "DEL" puts Delhi at the top.
 */
export function filterAirports(airports: readonly Airport[], query: string, limit = 8): Airport[] {
  const q = query.trim().toLowerCase();
  if (q === '') {
    return airports.slice(0, limit);
  }
  const score = (a: Airport): number => {
    const code = a.iataCode.toLowerCase();
    const city = cityName(a).toLowerCase();
    if (code === q) return 0;
    if (city.startsWith(q)) return 1;
    if (code.startsWith(q)) return 2;
    if (a.name.toLowerCase().includes(q) || city.includes(q)) return 3;
    if ((a.city?.countryName ?? '').toLowerCase().includes(q)) return 4;
    return -1;
  };
  return airports
    .map((airport) => ({ airport, rank: score(airport) }))
    .filter((entry) => entry.rank >= 0)
    .sort((x, y) => x.rank - y.rank)
    .slice(0, limit)
    .map((entry) => entry.airport);
}
