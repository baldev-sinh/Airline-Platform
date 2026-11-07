import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { AirportDirectory, cityName } from '../../core/api/airport-directory';
import { FlightApi } from '../../core/api/flight-api';
import { errorMessage } from '../../core/http/api-error';
import { Airport, FlightInstance } from '../../core/models/api-models';
import { EmptyState, ErrorState, Loading } from '../../shared/async-state';
import { CABIN_LABELS } from '../../shared/labels';
import { addDaysIso, todayIso } from '../../shared/validators';
import { FlightCard } from './flight-card';
import {
  SORT_OPTIONS,
  SortOrder,
  TIME_WINDOWS,
  TimeWindow,
  airlinesIn,
  applyFilters,
} from './flight-filters';
import { SearchCriteria, parseCriteria, toQueryParams } from './search-criteria';
import { SearchForm } from './search-form';

/** What the search returns, plus the two airports (for headings like "Mumbai to Delhi"). */
interface SearchResult {
  readonly origin: Airport | undefined;
  readonly destination: Airport | undefined;
  readonly flights: readonly FlightInstance[];
}

/**
 * /flights?from=BOM&to=DEL&date=2026-11-20&pax=1&cabin=ECONOMY
 *
 * Data flow: URL query params -> inputs (thanks to withComponentInputBinding) -> `criteria`
 * (computed) -> `results` (rxResource re-fetches whenever criteria change) -> filtered list.
 */
@Component({
  selector: 'app-search-results-page',
  imports: [DatePipe, RouterLink, SearchForm, FlightCard, Loading, ErrorState, EmptyState],
  templateUrl: './search-results-page.html',
  styleUrl: './search-results-page.scss',
})
export class SearchResultsPage {
  // Query params arrive as inputs with the same names.
  readonly from = input<string>();
  readonly to = input<string>();
  readonly date = input<string>();
  readonly pax = input<string>();
  readonly cabin = input<string>();

  private readonly router = inject(Router);
  private readonly flightApi = inject(FlightApi);
  private readonly airports = inject(AirportDirectory);

  protected readonly criteria = computed<SearchCriteria | null>(() =>
    parseCriteria({
      from: this.from(),
      to: this.to(),
      date: this.date(),
      pax: this.pax(),
      cabin: this.cabin(),
    }),
  );

  /**
   * rxResource = "load this, and load it again when its params change". It exposes signals:
   * isLoading(), error(), hasValue(), value(), plus reload(). Returning undefined from params
   * means "nothing to load" (an invalid URL).
   */
  protected readonly results = rxResource({
    params: () => this.criteria() ?? undefined,
    stream: ({ params: criteria }) =>
      forkJoin({
        origin: this.airports.findByCode(criteria.from),
        destination: this.airports.findByCode(criteria.to),
      }).pipe(
        switchMap(({ origin, destination }) => {
          if (!origin || !destination) {
            return of<SearchResult>({ origin, destination, flights: [] });
          }
          return this.flightApi
            .search({
              fromAirportId: origin.id,
              toAirportId: destination.id,
              date: criteria.date,
              passengers: criteria.passengers,
              cabin: criteria.cabin,
            })
            .pipe(map((page): SearchResult => ({ origin, destination, flights: page.items })));
        }),
      ),
  });

  // ----- Filters (kept in memory; the list is small, so filtering is instant) -----
  protected readonly sortOptions = SORT_OPTIONS;
  protected readonly timeWindows = TIME_WINDOWS;
  protected readonly sort = signal<SortOrder>('departure');
  protected readonly windows = signal<readonly TimeWindow[]>([]);
  protected readonly airlineIds = signal<readonly number[]>([]);
  protected readonly showModify = signal(false);
  protected readonly showFilters = signal(false);

  protected readonly allFlights = computed(() =>
    this.results.hasValue() ? this.results.value().flights : [],
  );
  protected readonly airlines = computed(() => airlinesIn(this.allFlights()));
  protected readonly visibleFlights = computed(() =>
    applyFilters(this.allFlights(), {
      sort: this.sort(),
      windows: this.windows(),
      airlineIds: this.airlineIds(),
    }),
  );
  protected readonly activeFilterCount = computed(
    () => this.windows().length + this.airlineIds().length,
  );

  protected readonly routeTitle = computed(() => {
    const value = this.results.hasValue() ? this.results.value() : null;
    const c = this.criteria();
    const name = (airport: Airport | undefined, code: string) =>
      airport ? cityName(airport) : code;
    return c ? `${name(value?.origin, c.from)} to ${name(value?.destination, c.to)}` : '';
  });

  protected readonly unknownAirport = computed(() => {
    const value = this.results.hasValue() ? this.results.value() : null;
    return !!value && (!value.origin || !value.destination);
  });

  protected readonly cabinLabels = CABIN_LABELS;
  protected readonly today = todayIso();

  /** Yesterday / tomorrow buttons, the quickest way to compare days. */
  protected readonly nearbyDays = computed(() => {
    const c = this.criteria();
    if (!c) {
      return [];
    }
    return [-1, 1]
      .map((offset) => addDaysIso(c.date, offset))
      .map((date) => ({
        date,
        disabled: date < this.today,
        params: toQueryParams({ ...c, date }),
      }));
  });

  protected readonly errorText = computed(() => errorMessage(this.results.error()));

  protected toggleWindow(window: TimeWindow): void {
    this.windows.update((list) =>
      list.includes(window) ? list.filter((w) => w !== window) : [...list, window],
    );
  }

  protected toggleAirline(id: number): void {
    this.airlineIds.update((list) =>
      list.includes(id) ? list.filter((a) => a !== id) : [...list, id],
    );
  }

  protected clearFilters(): void {
    this.windows.set([]);
    this.airlineIds.set([]);
  }

  protected onSortChange(value: string): void {
    this.sort.set(value as SortOrder);
  }

  protected select(flight: FlightInstance): void {
    const c = this.criteria();
    if (!c) {
      return;
    }
    void this.router.navigate(['/book', flight.id], {
      queryParams: { cabin: c.cabin, pax: c.passengers, fare: flight.fare?.id },
    });
  }

  /** Used by the "no results" view. */
  protected readonly hasFiltersHidingEverything = computed(
    () => this.allFlights().length > 0 && this.visibleFlights().length === 0,
  );
}
