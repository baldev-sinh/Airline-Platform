# Learning Angular through the Baldev Airways web app

This guide teaches Angular from zero, using **this project's real code** as the textbook. Every
concept is introduced the moment you need it, with the file where it lives, so you can open the code
side by side and read along.

- New to the project? Read the sections in order. Each one builds on the one before.
- Need a quick reference instead (commands, folder map, routes table)? See
  [frontend/README.md](../frontend/README.md).
- Want to click through the app by hand? See [FRONTEND_TESTING.md](FRONTEND_TESTING.md).

You only need basic JavaScript. TypeScript bits are explained as they come up.

---

## Contents

1. [What Angular is, and what a single-page app is](#1-what-angular-is-and-what-a-single-page-app-is)
2. [Running it and the tools around it](#2-running-it-and-the-tools-around-it)
3. [From an empty browser tab to the first screen](#3-from-an-empty-browser-tab-to-the-first-screen)
4. [Components: the building blocks](#4-components-the-building-blocks)
5. [Templates: HTML with superpowers](#5-templates-html-with-superpowers)
6. [Signals: values the screen follows](#6-signals-values-the-screen-follows)
7. [Services and dependency injection](#7-services-and-dependency-injection)
8. [Talking to the backend: HttpClient and RxJS](#8-talking-to-the-backend-httpclient-and-rxjs)
9. [Interceptors and error handling](#9-interceptors-and-error-handling)
10. [Loading data on a page: rxResource](#10-loading-data-on-a-page-rxresource)
11. [Routing: pages and URLs](#11-routing-pages-and-urls)
12. [Signing in: AuthStore, JWT and guards](#12-signing-in-authstore-jwt-and-guards)
13. [Forms](#13-forms)
14. [The whole booking journey, traced end to end](#14-the-whole-booking-journey-traced-end-to-end)
15. [Styling](#15-styling)
16. [Accessibility](#16-accessibility)
17. [Security in the browser](#17-security-in-the-browser)
18. [Testing](#18-testing)
19. [Building and shipping to production](#19-building-and-shipping-to-production)
20. [Recipe: adding a new page](#20-recipe-adding-a-new-page)
21. [What a production team would still add](#21-what-a-production-team-would-still-add)
22. [Glossary](#22-glossary)

---

## 1. What Angular is, and what a single-page app is

A traditional website asks the server for a **new HTML page** on every click. A **single-page app
(SPA)** downloads one HTML page and a bundle of JavaScript **once**. After that, JavaScript redraws
parts of the page and changes the address bar itself. It only goes to the server for **data**
(JSON), never for new pages.

```
Traditional site                       Single-page app (this project)
----------------                       ------------------------------
click -> server builds HTML -> show    first visit: download index.html + JS
click -> server builds HTML -> show    click -> JS redraws the screen, fetches JSON if needed
```

**Angular** is a framework (made by Google) for building SPAs. It gives you, out of the box:

| Need | Angular's answer | Where you'll see it here |
|---|---|---|
| Split the UI into reusable pieces | Components | every `*.ts` + `*.html` pair |
| Keep the screen in sync with data | Signals + templates | `signal()`, `computed()` everywhere |
| Share logic and state | Services + dependency injection | `core/api/*`, `AuthStore`, `CheckoutStore` |
| Different "pages" at different URLs | Router | `app.routes.ts` |
| Call the backend | HttpClient | `core/api/*` |
| Forms with validation | Reactive Forms | login, search, travellers |
| Tests | TestBed (+ Vitest here) | `*.spec.ts` |
| Build an optimized bundle | Angular CLI | `ng build` |

Angular apps are written in **TypeScript**: JavaScript plus types. Types let the compiler catch
mistakes like "you passed a string where a number was expected" before the code ever runs.
This project turns TypeScript up to its strictest settings (see `tsconfig.json`:
`"strict": true`, `"strictTemplates": true`), so even mistakes **inside HTML templates** are compile
errors. That is a production practice: bugs are caught on your machine, not by customers.

---

## 2. Running it and the tools around it

```bash
cd frontend
npm install     # downloads dependencies into node_modules/ (once)
npm start       # dev server on http://localhost:5173
npm test        # unit tests, re-run on save
npm run lint    # code-quality and accessibility checks
npm run build   # optimized production build into dist/
```

You need Node 24 (pinned in `.nvmrc`). For real data, the backend must be running on port 5000
(see the root README).

### The files at the root of `frontend/`, one by one

| File | What it is | Why it matters |
|---|---|---|
| `package.json` | The list of libraries the app uses and the `npm` scripts above. | `dependencies` ship to the browser; `devDependencies` are only build/test tools. |
| `package-lock.json` | Exact versions of every library, including indirect ones. | Everyone (and the Docker build, via `npm ci`) installs byte-for-byte the same thing. Always commit it. |
| `angular.json` | Configuration for the Angular CLI: how to build, serve, test and lint. | Holds the **production** vs **development** build settings (section 19). |
| `tsconfig*.json` | TypeScript compiler settings. `tsconfig.app.json` is for app code, `tsconfig.spec.json` for tests. | Strict mode lives here. |
| `proxy.conf.json` | Tells the dev server: send `/api/*` and `/auth/*` to `http://localhost:5000`. | The browser thinks the API is on the same address as the app, so no CORS setup is needed in development, just like production. |
| `eslint.config.js` | Lint rules, including `templateAccessibility` for HTML. | Catches bad patterns and accessibility mistakes automatically. |
| `.prettierrc` | Code formatter settings (100 chars, single quotes). | Nobody argues about formatting in reviews. |
| `.editorconfig`, `.vscode/` | Editor settings and recommended extensions. | Consistent indentation everywhere. |
| `Dockerfile`, `.dockerignore`, `nginx/` | How the app is packaged and served in production. | Section 19. |
| `public/` | Files copied as-is into the build (`favicon.svg`, `robots.txt`). | |
| `src/` | The actual application. | Everything else in this guide. |
| `dist/`, `.angular/`, `node_modules/` | Generated. Never edit or commit them. | |

### The folders inside `src/app/`

```
app/
  app.ts / app.html / app.scss   the page frame (header, <main>, footer)
  app.config.ts                  app-wide wiring, read once at start-up
  app.routes.ts                  which URL shows which page
  core/        things used everywhere and created once (API clients, auth, HTTP, layout)
  shared/      small reusable UI pieces (money pipe, field errors, loading/error states)
  features/    one folder per area of the site (home, flight-search, checkout, payment, trips...)
```

The dependency rule: **features** may use **core** and **shared**. **shared** never imports from
**features**. That keeps the pieces from tangling into a ball where everything depends on
everything.

---

## 3. From an empty browser tab to the first screen

Here is what happens, step by step, when someone opens the site.

```
browser requests /            nginx (or the dev server) returns src/index.html
        |
index.html has <app-root>     an unknown tag for now, just an empty element
and loads main-XXXX.js
        |
main.ts runs                  bootstrapApplication(App, appConfig)
        |
appConfig is applied          router, HttpClient, interceptors, locale, error handler
        |
App component renders         into <app-root>: skip link, header, <main>, footer
        |
Router looks at the URL       "/" -> lazy-loads HomePage -> renders it in <router-outlet>
```

### `src/index.html`

```html
<body>
  <app-root ngCspNonce="CSP_NONCE"></app-root>
  <noscript>Please turn on JavaScript ...</noscript>
</body>
```

`<app-root>` is a placeholder. Angular finds the component whose `selector` is `'app-root'` and
renders it there. `ngCspNonce` is a security detail covered in section 17.

### `src/main.ts`: the entry point

```ts
bootstrapApplication(App, appConfig).catch((err) => console.error(err));
```

One line: "start the app with `App` as the root component, using the settings in `appConfig`."

### `src/app/app.config.ts`: the wiring

This file lists **providers**: things the whole app can ask for. Read it as a table of contents of
the app's infrastructure:

```ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),                    // catch uncaught errors and rejected promises
    { provide: ErrorHandler, useClass: GlobalErrorHandler }, // ...and send them to OUR handler
    { provide: LOCALE_ID, useValue: 'en-IN' },               // ₹5,300, "20 Nov 2026"

    provideRouter(
      routes,
      withComponentInputBinding(),          // URL params arrive as component inputs (section 11)
      withInMemoryScrolling({ ... }),       // scroll to top on navigation, restore on Back
      withPreloading(PreloadAllModules),    // fetch other pages' code in the background
    ),
    { provide: TitleStrategy, useClass: PageTitleStrategy }, // "My trips | Baldev Airways"

    provideHttpClient(
      withInterceptors([errorInterceptor, authInterceptor, retryInterceptor]), // section 9
    ),
  ],
};
```

`provideHttpClient` sends requests with the browser's modern `fetch()` API; that is the default in
this Angular version. (Older tutorials add `withFetch()` for this; it is now deprecated and does
nothing. The opposite, `withXhr()`, switches to the older `XMLHttpRequest`, which you'd only need
for upload progress bars. This app only sends small JSON bodies.)

**Tip: deprecation warnings.** When your editor strikes through a name or says "marked as
deprecated", hover it and read the `@deprecated` note. It usually says what to use instead. Fix
these when you see them: deprecated APIs are removed in a later major version.

`{ provide: X, useClass: Y }` means "whenever someone asks for X, give them a Y". It is how you
swap Angular's default behaviour for your own. Section 7 explains this pattern.

### `src/app/app.ts` + `app.html`: the root component

```html
<a class="skip-link no-print" href="#main">Skip to main content</a>
<app-site-header />
<main id="main" #main tabindex="-1">
  <router-outlet />
</main>
<app-site-footer />
```

The header and footer stay on screen. `<router-outlet />` is the hole where the current page goes.
When the URL changes, the router swaps out what's inside it.

The constructor in `app.ts` moves keyboard focus to `<main>` after each navigation. Why? In an SPA
the browser never "loads a new page", so screen-reader users would not notice anything changed.
This is the kind of detail that separates a demo from a production app.

---

## 4. Components: the building blocks

A **component** is a piece of screen with its own HTML, CSS and behaviour. Pages are components;
the pieces inside pages are components too. Let's dissect a simple one:
`features/flight-search/flight-card.ts`, which draws one flight in the results list.

```ts
@Component({
  selector: 'app-flight-card',              // used in HTML as <app-flight-card />
  imports: [DatePipe, MoneyPipe, AirlineMark], // what this template is allowed to use
  templateUrl: './flight-card.html',        // its HTML
  styleUrl: './flight-card.scss',           // its CSS (scoped: affects only this component)
})
export class FlightCard {
  readonly flight = input.required<FlightInstance>(); // data IN from the parent
  readonly passengers = input(1);                     // optional input with a default
  readonly selected = output<FlightInstance>();       // event OUT to the parent

  protected readonly seatsLeft = computed(() => {     // derived value (section 6)
    const seats = this.flight().availableSeats;
    return seats > 0 && seats <= LOW_SEATS ? seats : null;
  });
}
```

Piece by piece:

- **`@Component({...})`** is a *decorator*: metadata that tells Angular "this class is a
  component, and here is how to draw it".
- **`selector`** is the custom HTML tag. The project's lint rules require the `app-` prefix so you
  can tell your own components from native HTML tags at a glance.
- **`imports`** lists every other component, directive or pipe the template uses. If you forget
  one, the build fails. These are called **standalone components** (no `NgModule` files, which older
  Angular tutorials use; you can ignore `NgModule` entirely here).
- **`styleUrl`** styles are *scoped*: a `.title` rule in `flight-card.scss` can't accidentally
  restyle a `.title` elsewhere. Angular does this by adding unique attributes behind the scenes.
- **`input()`** declares data the parent passes in. **`output()`** declares events the component
  sends out. The parent uses them like this (from `search-results-page.html`):

  ```html
  <app-flight-card
    [flight]="flight"
    [passengers]="c.passengers"
    (selected)="select($event)"
  />
  ```

  Square brackets `[ ]` push data **in**. Round brackets `( )` listen for events coming **out**.
  `$event` is the value the child emitted.

- **`protected`** members are visible to the template but not to other classes. **`private`**
  members are for the class only. Public `readonly` members are the component's API (its inputs
  and outputs). This makes it obvious what other code may touch.

### Presentational vs smart components

`FlightCard` never calls the API. It receives data and reports clicks. That is a
**presentational** component: easy to reuse and test. `SearchResultsPage` is a **smart** (or
container) component: it fetches data, holds the filters, and decides what happens when a card is
selected. Keeping most components presentational is a big part of keeping a large app manageable.

### Inline templates

Small components can put their HTML and CSS directly in the decorator with `template:` and
`styles:` instead of separate files. See `shared/async-state.ts` (`Loading`, `ErrorState`,
`EmptyState`) and `checkout/price-summary.ts`.

### Content projection: `<ng-content>`

`EmptyState` contains `<ng-content />`. Whatever you put between its tags is placed there:

```html
<app-empty-state title="No flights on this date">
  <p class="muted">Try a nearby date.</p>   <!-- lands where <ng-content /> is -->
</app-empty-state>
```

---

## 5. Templates: HTML with superpowers

A template is HTML plus a small set of Angular syntax. Here is all of it as it is used in this app.

| Syntax | Meaning | Example from this project |
|---|---|---|
| `{{ expr }}` | Insert text (auto-escaped, so it is safe against injected HTML) | `{{ routeTitle() }}` |
| `[prop]="expr"` | Set a DOM property or component input | `[disabled]="submitting()"` |
| `[attr.x]="expr"` | Set an HTML *attribute* (for ARIA etc.) | `[attr.aria-expanded]="menuOpen()"` |
| `[class.x]="bool"` | Add/remove a CSS class | `[class.is-open]="menuOpen()"` |
| `(event)="stmt"` | Run code on an event | `(click)="toggleMenu()"` |
| `#name` | A *template reference* to an element | `<main #main>` (used with `viewChild`) |
| `\| pipe` | Format a value | `{{ c.date \| date: 'EEE d MMM y' }}`, `{{ p.fare \| money }}` |

### Control flow blocks

```html
@if (results.isLoading()) {
  <app-loading label="Finding the best flights for you" />
} @else if (results.error()) {
  <app-error-state ... />
} @else {
  ...
}
```

```html
@for (flight of visibleFlights(); track flight.id) {
  <li><app-flight-card [flight]="flight" /></li>
}
```

`track flight.id` tells Angular how to recognise the same item between redraws. When filters
change, Angular moves existing cards instead of destroying and recreating all of them. Always
track by a stable id. Inside `@for` you also get helpers like `$index` and `$first`
(see the "nearby days" links in `search-results-page.html`).

```html
@switch (store.step()) {
  @case ('fare') { <app-fare-step /> }
  @case ('travellers') { <app-travellers-step /> }
  ...
}
```

Two handy forms:

- `@if (auth.user(); as user) { Hi, {{ user.fullName }} }`: test a value and give it a name
  (`site-header.html`).
- `@let c = criteria();`: a local variable in the template (`search-results-page.html`), so you
  don't repeat `criteria()` everywhere.

### Pipes

A **pipe** transforms a value for display. `DatePipe` is built in. `MoneyPipe`
(`shared/money-pipe.ts`) is custom:

```ts
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);
  transform(amount: number | null | undefined, currency?: string | null): string { ... }
}
```

Pipes are *pure* by default: they re-run only when their input changes, so they're cheap even in
long lists. Formatting money once, in one place, means "₹5,300" looks identical on every page.

### Dates in this app

The backend sends flight times as **local airport time without a time zone**
(`"2026-11-20T09:30:00"`). The app shows them exactly as they are and never converts them to the
browser's zone. A Mumbai departure at 09:30 must read 09:30 even for someone browsing from London.
This rule is written at the top of `core/models/api-models.ts`.

---

## 6. Signals: values the screen follows

This is the most important idea in modern Angular.

A **signal** is a box holding a value. When the value changes, everything that reads it updates
automatically, including the screen.

```ts
const menuOpen = signal(false);   // create
menuOpen();                       // read  -> false   (note the parentheses: it's a function)
menuOpen.set(true);               // replace the value
menuOpen.update((open) => !open); // change it based on the old value
```

A **computed** signal is derived from other signals. You never set it; it recalculates itself when
something it reads changes, and caches the result until then:

```ts
// features/flight-search/search-results-page.ts
protected readonly visibleFlights = computed(() =>
  applyFilters(this.allFlights(), {
    sort: this.sort(),
    windows: this.windows(),
    airlineIds: this.airlineIds(),
  }),
);
```

Tick a filter checkbox -> `windows` changes -> `visibleFlights` recomputes -> the list redraws ->
the "N flights" count (which also reads `visibleFlights()`) updates. No manual wiring anywhere.

An **effect** runs side-effect code when signals it reads change. Use it sparingly, for syncing
with something outside the signal world. In `search-form.ts`, an effect copies the `initial` input
into the form whenever it arrives:

```ts
effect(() => {
  const criteria = this.initial();
  if (criteria) { this.form.setValue({ ... }); }
});
```

Rule of thumb: **derive with `computed`, react with `effect` only when you must touch something
that isn't a signal** (a form, the DOM, storage).

### Why signals matter for performance: zoneless + OnPush

Older Angular re-checked *the whole page* after every click, timer or HTTP response "just in
case" (a library called zone.js triggered this). This app is **zoneless** and its components use
**OnPush** change detection (both defaults in this Angular version). Angular only re-renders a
component when a signal or input it uses actually changed. It's faster, and easier to reason about:
the screen changes because a signal changed, never by accident.

The consequence you must remember: **if state isn't in a signal (or an input), the screen won't
notice it changed.** That's why `shared/field-error.ts` turns a form control's `events` stream into
a signal. Form controls aren't signals, so without that bridge an error message would not appear.

---

## 7. Services and dependency injection

A **service** is a plain class that holds logic or state shared by several components. Examples:
`FlightApi` (talks to the flight endpoints), `AuthStore` (who is signed in).

```ts
@Injectable({ providedIn: 'root' })
export class FlightApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  ...
}
```

**Dependency injection (DI)** means a class doesn't create the things it needs; it **asks** for
them with `inject(...)`, and Angular hands them over. Why bother?

1. **One shared instance.** `providedIn: 'root'` means one `AuthStore` for the whole app, so every
   component sees the same signed-in user.
2. **Easy testing.** A test can say "when someone asks for `AuthStore`, give them this fake"
   (section 18), without changing the code under test.
3. **Tree-shaking.** A `providedIn: 'root'` service that nobody uses is dropped from the bundle.

`inject()` only works while Angular is creating something (in a field initializer or the
constructor). This is called the **injection context**. Calling `inject()` later, say inside a click
handler, throws an error.

### Asking for values, not just classes: `InjectionToken`

`core/config/app-config.ts`:

```ts
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});
```

A token is a "name" you can inject a plain value by: `inject(API_BASE_URL)`. Classes never import
the environment file directly, so tests can provide a different URL in one line.

### Scoped services: one per page, not one per app

`CheckoutStore` is *not* `providedIn: 'root'`. Instead, `CheckoutPage` lists it:

```ts
@Component({
  selector: 'app-checkout-page',
  providers: [CheckoutStore],   // a NEW store each time this page opens
  ...
})
```

All five step components inside the page `inject(CheckoutStore)` and get **the same** instance.
When the customer leaves the page, the store is destroyed with it. So a half-finished booking can
never leak into the next one. Picking the right **scope** for state is a key design decision:

| State | Scope | Why |
|---|---|---|
| Signed-in user | app (`root`) | Needed everywhere, lives the whole visit |
| Airport list | app (`root`), cached | Rarely changes, used by several pages |
| Checkout choices | one checkout page | Must start fresh for every booking |
| "Is the mobile menu open?" | one component (`signal` field) | Nobody else cares |

### "Api" classes vs "Store" classes

This project uses a clear naming convention:

- **`*Api`** (`core/api/*.ts`, `auth/auth-api.ts`): only HTTP calls. No state.
- **`*Store`** (`AuthStore`, `CheckoutStore`): state in signals, plus methods that change it.

---

## 8. Talking to the backend: HttpClient and RxJS

### The models: TypeScript shapes of the JSON

`core/models/api-models.ts` describes every JSON object the backend sends or receives, each mirroring
a Java class in `common-lib`:

```ts
/** BookingResponse */
export interface Booking {
  id: number;
  bookingReference: string;
  status: BookingStatus;           // 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'
  ...
}
```

An `interface` is only for the compiler. It produces no JavaScript. It means that if you type
`booking.staus`, the build fails. If the backend renames a field, this is the one file to update,
and the compiler then points at every place that breaks.

Note the enum-like types such as `'PENDING' | 'CONFIRMED' | ...`. Combined with
`Record<BookingStatus, ...>` in `shared/labels.ts`, TypeScript forces you to add a label when a new
status appears. Forgetting is a compile error, not a blank badge in production.

### An API class

```ts
// core/api/booking-api.ts
get(id: number): Observable<Booking> {
  return this.http.get<Booking>(`${this.baseUrl}/api/bookings/${id}`);
}
```

`http.get` does **not** send the request yet. It returns an **Observable**: a description of work
that will happen when someone *subscribes*.

### RxJS in five minutes

An **Observable** is a stream that can deliver values over time, then finish or fail. An HTTP call
is a stream that delivers one value (the response) and finishes. A timer is a stream that delivers a
value every N ms.

```ts
bookingApi.get(42).subscribe({
  next: (booking) => { /* got it */ },
  error: (err) => { /* failed */ },
});
```

You shape streams with **operators** inside `.pipe(...)`. The ones this app uses:

| Operator | What it does | Where |
|---|---|---|
| `map` | Transform each value | `booking-api.ts` sorts the history newest-first |
| `tap` | Do something on the side, value passes through unchanged | `AuthStore.login` saves the session |
| `switchMap` | For each value, start a new stream (and cancel the previous one) | results page: find airports, *then* search flights |
| `forkJoin` | Run several streams in parallel, emit once **all** have finished | checkout loads flight + cabin at the same time |
| `catchError` | Replace an error with something else | checkout: meals failing -> `of([])`, checkout still works |
| `shareReplay(1)` | Run once, give the cached result to later subscribers | `AirportDirectory.airports$` |
| `retry` | Try again on failure | `retryInterceptor` |
| `timer`, `takeWhile`, `last`, `concat`, `defer` | Timing and sequencing | payment polling (section 14) |
| `takeUntilDestroyed` | Unsubscribe automatically when the component is destroyed | `site-header.ts`, `app.ts` |

Two production habits to notice:

1. **Unsubscribing.** A subscription to an endless stream (like `router.events`) that is never
   closed keeps running after the component is gone: a memory leak. `takeUntilDestroyed()` closes it
   for you. HTTP calls finish on their own, so they don't need it.
2. **Degrading gracefully.** In `CheckoutStore.load`, meals and extras are wrapped in
   `catchError(() => of([]))`. If that microservice is down, customers can still book a flight,
   just without meals. Optional data should never block the main job.

### Signals vs Observables: which when?

- **Signals** hold *current state* ("the selected fare is #17"). Great for templates.
- **Observables** describe *events and async work over time* ("fetch this, then poll every 2 s").

The bridges: `toSignal(obs$)` turns a stream into a signal (`airport-field.ts`), `toObservable(sig)`
does the opposite (`field-error.ts`), and `rxResource` (section 10) runs an Observable whenever
signals change.

### Spring pages

Spring Boot returns lists in pages, in one of two JSON shapes depending on version.
`core/models/page.ts`'s `toPage()` accepts both and gives the app one simple `Page<T>`. That's an
**adapter**: absorb the backend's quirks at the edge, keep the rest of the app clean.

---

## 9. Interceptors and error handling

### Interceptors: a pipeline every request passes through

Registered in `app.config.ts` in this order:

```
your code -> errorInterceptor -> authInterceptor -> retryInterceptor -> network
                    ^                                                       |
                    +------------------- response / error ------------------+
```

The first one listed is the **outermost**, so `errorInterceptor` sees the final result, after
retries. All three live in `core/http/interceptors.ts`:

- **`authInterceptor`** adds `Authorization: Bearer <token>`, **but only for our own API**
  (`isApiRequest`). Sending the token to any other host (a CDN, a payment site) would leak the
  customer's session. Note the check that `//evil.example` (a protocol-relative URL, meaning
  *another site*) does not count as "ours".
- **`retryInterceptor`** retries **GET only**, twice, with growing delays (0.5 s, 1 s), and only for
  "temporary" failures (no connection, 502/503/504). A POST is never retried automatically: if the
  first "create booking" actually reached the server, a retry could create a second booking.
  Knowing which operations are safe to repeat (*idempotent*) is a core production skill.
- **`errorInterceptor`** converts every failure into an `ApiError`. On a 401 (token rejected) it
  signs the user out and sends them to `/login?returnUrl=...&reason=expired`. A 401 from
  `/auth/login` itself just means "wrong password", so it's left for the login form.

### `ApiError`: one error shape for the whole app

`core/http/api-error.ts`. The backend sends RFC 9457 "problem details":

```json
{ "status": 409, "detail": "Seat 12A is no longer available", "code": "SEAT_UNAVAILABLE" }
```

`toApiError()` turns that, or any failure, into:

- `status`: the HTTP status (0 = never reached the server).
- `code`: stable text that **code** can branch on (`'SEAT_UNAVAILABLE'`). Never branch on the
  human message: wording changes, codes don't.
- `message`: text that is **safe to show to the customer**. For 5xx errors the server's text is
  replaced with a friendly fallback, because server messages are written for engineers and can leak
  internals.
- `fieldErrors`: per-field validation messages.

### `GlobalErrorHandler`: the last safety net

`core/error/global-error-handler.ts` catches anything nobody else handled. Two production details:

1. **Stale code after a release.** If a tab was open during a deployment, it may try to load
   `checkout-OLDHASH.js`, which no longer exists. The handler spots that error and reloads the page
   once (at most once a minute, so a truly broken release can't cause an endless reload loop).
2. **A hook for monitoring.** The `TODO(monitoring)` is where Sentry or Datadog reporting would go.

---

## 10. Loading data on a page: rxResource

Almost every page does the same thing: "load data based on something (the URL, an id), show a
spinner, then the data or an error". `rxResource` packages that:

```ts
// features/trips/trips-page.ts
protected readonly bookings = rxResource({ stream: () => this.bookingApi.myBookings() });
```

It gives you signals to use in the template:

| Signal / method | Meaning |
|---|---|
| `isLoading()` | Request in flight |
| `error()` | The error, if it failed |
| `hasValue()` / `value()` | The data, once it arrived |
| `reload()` | Fetch again (wired to "Try again" buttons) |

With **`params`**, it re-fetches whenever the params change:

```ts
// features/flight-search/search-results-page.ts
protected readonly results = rxResource({
  params: () => this.criteria() ?? undefined,   // undefined = "nothing to load"
  stream: ({ params: criteria }) => /* find airports, then search flights */,
});
```

Click "next day" -> URL changes -> `date` input changes -> `criteria` recomputes -> `results`
re-fetches. If the previous request is still running, it is cancelled, so a slow old response can
never overwrite a newer one (a classic SPA bug called a *race condition*).

Every data page then uses the same three shared components from `shared/async-state.ts`:

```html
@if (results.isLoading()) {
  <app-loading label="Finding the best flights for you" />
} @else if (results.error()) {
  <app-error-state [message]="errorText()" (retry)="results.reload()" />
} @else if (allFlights().length === 0) {
  <app-empty-state title="No flights on this date">...</app-empty-state>
} @else {
  ...the real content
}
```

**Loading, error, empty, content:** a production page handles all four states. Demo apps usually
only handle the last one.

---

## 11. Routing: pages and URLs

`src/app/app.routes.ts` maps URLs to page components:

```ts
{
  path: 'trips/:bookingId',            // :bookingId is a parameter, e.g. /trips/42
  title: 'Trip details',               // browser tab title
  canActivate: [requireSignIn],        // a guard runs first (section 12)
  loadComponent: () =>                 // lazy loading
    import('./features/trips/trip-detail-page').then((m) => m.TripDetailPage),
},
{ path: '**', ... NotFoundPage },      // anything else: 404 page. Must be last.
```

### Lazy loading

`loadComponent: () => import(...)` means the checkout code is **not** in the first download. It is
fetched only when someone opens a checkout. The first visit stays small and fast. After the first
page shows, `withPreloading(PreloadAllModules)` quietly downloads the rest in the background, so
later clicks are instant too.

### Links and navigation

In templates: `<a routerLink="/trips">` (with `routerLinkActive="is-active"` to highlight the
current menu item). In code: `router.navigate(['/book', flight.id], { queryParams: {...} })`.
`routerLink` changes the URL without reloading the page. A plain `href` would reload the entire
app.

### URL values arrive as inputs

Thanks to `withComponentInputBinding()`, path params and query params become component inputs
with the same name:

```ts
// /flights?from=BOM&to=DEL&date=2026-11-20&pax=2&cabin=ECONOMY
export class SearchResultsPage {
  readonly from = input<string>();
  readonly to = input<string>();
  readonly date = input<string>();
  ...
}
```

### The URL as the source of truth

The flight search lives **in the URL** (`search-criteria.ts`), not only in memory. So results can
be bookmarked, shared, refreshed, and the Back button works. Because anyone can type anything
into a URL, `parseCriteria()` validates every value and returns `null` for garbage. The page then
shows "that link is incomplete" with a fresh search form instead of crashing. `CheckoutPage` does
the same for `?cabin=&pax=&fare=`. **Treat URL values as untrusted user input.**

### Page titles

`core/layout/page-title-strategy.ts` turns each route's `title` into `"My trips | Baldev Airways"`.
Good titles help people with many tabs open, screen-reader users (titles are announced), and
search engines.

### How deep links work in production

If someone opens `https://site/trips/42` directly, the server has no file called `trips/42`. nginx
is configured to answer **any unknown path with `index.html`** (`try_files $uri $uri/ /index.html`),
then the Angular router reads the URL and shows the right page (section 19).

---

## 12. Signing in: AuthStore, JWT and guards

### The token

On login, the backend returns a **JWT** (JSON Web Token): a signed string like `xxx.yyy.zzz`. The
middle part is base64-encoded JSON such as `{ "sub": "42", "exp": 1762500000 }`. The app sends it
on every API call, and the API gateway verifies the signature.

`core/auth/jwt.ts` reads **only** `exp` (when the token expires). The browser can't verify the
signature (the secret lives on the server) and doesn't need to: a tampered token is rejected by the
gateway. **Never trust anything in the browser for security decisions.** The browser is the
customer's machine, and they can change anything on it.

### `AuthStore`: the one source of truth for "who is signed in"

`core/auth/auth-store.ts`:

```ts
private readonly session = signal<Session | null>(null);
readonly user = computed(() => this.session()?.user ?? null);
readonly isSignedIn = computed(() => this.session() !== null);
```

What it handles, and why each matters:

| Behaviour | Why |
|---|---|
| Saves the session to `localStorage` | A page refresh doesn't sign you out |
| Sets a timer for the token's expiry and signs out then | No silent failures an hour later |
| `token()` re-checks expiry on every read | Covers a laptop that slept past the timer |
| Listens to the `storage` event | Sign out in one tab, every other tab follows |
| `sessionExpired` signal | The login page can say *why* you're there |
| Validates what it reads back from storage | Corrupt or tampered storage can't crash the app |
| Caps the timer at ~24.8 days | Browsers break with longer `setTimeout` values |

`core/util/browser-storage.ts` wraps `localStorage` in `try/catch`, because it **throws** in some
private-browsing modes or when storage is full. Without the wrapper, those users couldn't sign in
at all.

The comment at the top of `AuthStore` documents the **trade-off** of keeping the token in
`localStorage` (JavaScript can read it, so an XSS bug could steal it) and the mitigations: short
expiry, token sent only to our API, strict Content-Security-Policy. Writing down the trade-off is
itself a production practice. See section 21 for the alternative.

### Guards

`core/auth/auth-guards.ts`:

```ts
export const requireSignIn: CanActivateFn = (_route, state) => {
  if (inject(AuthStore).isSignedIn()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
```

A guard runs before a route opens. It returns `true` (go ahead) or a different URL (redirect).
After signing in, `LoginPage` navigates to `returnUrl`, so a guest who clicked "Select" on a flight
lands right back on that checkout.

**Guards are UX, not security.** Anyone can bypass browser code. The real protection is the
gateway checking the token on every request. Guards just send people to the right place.

`returnUrl` itself is untrusted (it's in the URL), so `safeReturnUrl()` in
`core/util/safe-redirect.ts` only accepts paths inside this app. Otherwise a link like
`/login?returnUrl=https://evil.example` would send people to a phishing site after a real login
(an **open redirect**).

---

## 13. Forms

Angular has two form styles. This app uses **Reactive Forms**, where the form is defined in
TypeScript. That makes it typed, testable, and good for complex rules.

### A simple form: login

`features/auth/login-page.ts`:

```ts
protected readonly form = inject(NonNullableFormBuilder).group({
  email: ['', [Validators.required, Validators.email]],
  password: ['', [Validators.required, Validators.maxLength(PASSWORD_MAX)]],
});
```

Each field is `[initialValue, validators]`. `NonNullableFormBuilder` makes `reset()` go back to
the initial value instead of `null`, and gives exact types (`form.getRawValue().email` is a
`string`).

Connecting it to HTML (`login-page.html`):

```html
<form [formGroup]="form" (ngSubmit)="submit()" novalidate>
  <label for="login-email">Email</label>
  <input id="login-email" type="email" formControlName="email"
         autocomplete="username" aria-describedby="login-email-error" />
  <app-field-error [control]="form.controls.email" errorId="login-email-error" label="Email" />
  ...
  <button type="submit" [disabled]="submitting()">
    {{ submitting() ? 'Signing in…' : 'Sign in' }}
  </button>
</form>
```

- `novalidate` turns off the browser's own popup bubbles so Angular's consistent messages are used.
- `autocomplete="username"` / `"current-password"` lets password managers work. Real users rely on
  them.

The submit handler shows the standard pattern:

```ts
protected submit(): void {
  if (this.form.invalid) {
    this.form.markAllAsTouched();   // reveal every error at once
    return;
  }
  this.submitting.set(true);        // disable the button: no double submits
  ...
  error: (err) => {
    // One message for wrong email OR wrong password, so attackers
    // can't discover which emails have accounts.
  }
}
```

### Showing errors: `FieldError`

`shared/field-error.ts` shows a message only after the field was **touched** (so users aren't
yelled at before they type), and turns Angular's error objects into sentences ("Email is
required"). It links itself to the input via `aria-describedby`, so screen readers read the error
too.

### Custom validators

`shared/validators.ts` holds rules shared by several forms (`pastDate`, `notInPast`,
`phoneNumber`). A validator is a function: given a control, return `null` (fine) or an error
object. They **mirror the backend's rules** so customers see problems instantly, but the server
still validates everything. Browser validation is a convenience, never security.

A **form-level** validator checks several fields together. `differentAirports` in
`search-form.ts` makes sure "From" and "To" differ.

### Dynamic forms: `FormArray`

The number of travellers is only known at runtime, so `CheckoutStore` builds a `FormArray` with
one group per passenger (`buildTravellerForm`), and pre-fills traveller 1 with the account holder's
name. The template loops over it with `formArrayName="passengers"` and `[formGroupName]="i"`
(`steps/travellers-step.html`). Each field gets a unique id like `p0-first`, `p1-first`, so labels
and error messages stay correctly linked.

### A custom form control: `AirportField`

`features/flight-search/airport-field.ts` is a type-ahead airport picker that works in a form
**exactly like a native input**: `<app-airport-field formControlName="from" />`. It does that by
implementing `ControlValueAccessor`, the four-method contract between Angular Forms and a control:

| Method | Called by Angular when... |
|---|---|
| `writeValue(code)` | the form sets a value (e.g. `setValue`, `reset`) |
| `registerOnChange(fn)` | it hands you a function to call when the user picks something |
| `registerOnTouched(fn)` | it hands you a function to call when the user leaves the field |
| `setDisabledState(bool)` | the control is disabled/enabled |

The `providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AirportField), multi: true }]`
line registers it. (`forwardRef` is needed because the class is referenced before it's defined.)

It also implements the WAI-ARIA **combobox** pattern: arrow keys, Enter, Escape,
`aria-activedescendant`. And it handles a subtle bug: options use `mousedown` instead of `click`,
because `click` fires *after* the input's `blur` closes the list, so the choice would be lost.

---

## 14. The whole booking journey, traced end to end

Now everything together. Follow a customer from search to e-ticket.

### Step 1: Home -> search (`features/home`, `features/flight-search/search-form.ts`)

The form validates, then **navigates** to
`/flights?from=BOM&to=DEL&date=2026-11-20&pax=2&cabin=ECONOMY`. It doesn't call the API itself:
the URL is the hand-off.

### Step 2: Results (`search-results-page.ts`)

```
URL query params -> inputs -> criteria (computed, validated)
   -> rxResource: AirportDirectory.findByCode(BOM), findByCode(DEL)   [forkJoin, cached list]
               -> FlightApi.search({ fromAirportId, toAirportId, ... })
   -> allFlights -> applyFilters(sort, time windows, airlines) -> visibleFlights -> FlightCards
```

The URL uses readable airport **codes**. The API wants database **ids**, so the page translates
using the cached airport list. Filters run in memory (the list for one route on one day is
small), so they are instant. Clicking "Select" navigates to `/book/123?cabin=ECONOMY&pax=2&fare=17`.

### Step 3: Checkout (`features/checkout/`)

`requireSignIn` runs first. `CheckoutPage` then:

1. Cleans the URL values into a typed `request` (bad values fall back to safe defaults).
2. `rxResource` -> `CheckoutStore.load()`: flight + cabin in parallel, then fares + meals + extras
   in parallel (meals/extras optional).
3. `blocker` checks if booking is even possible (flight closed? cabin not on sale? not enough
   seats?) and explains why instead of letting the customer fill in five steps for nothing.
4. Shows five steps, **all on one route**, sharing one `CheckoutStore`:

| Step | Component | What the store records |
|---|---|---|
| Fare | `steps/fare-step.ts` | `fareId` |
| Travellers | `steps/travellers-step.ts` | `travellerForm` |
| Seats | `steps/seat-step.ts` + `seat-layout.ts` | `seatIds[i]` per passenger, "choose for me" |
| Extras | `steps/extras-step.ts` | `mealIds[i]`, `extraQuantities` |
| Review & pay | `steps/review-step.ts` | `termsAccepted` |

`PriceSummary` beside the steps reads `store.price()`, a `computed` over every choice, so the total
updates live. It's labelled an **estimate**: the server always computes the amount actually
charged. Never trust the browser with money.

Moving between steps runs `store.validate(step)`. After each step change, focus moves to the new
heading (`afterNextRender` waits until it's on screen). Without that, keyboard and screen-reader
users would be left on a button that no longer exists.

### Step 4: Pay (`review-step.ts`)

```ts
this.store.createBooking().subscribe({
  next: (response) => {
    if (!isTrustedRedirect(response.checkoutUrl, this.trustedHosts, window.location.origin)) {
      // refuse: never send a customer to a payment page we don't recognise
    }
    window.location.assign(url);   // leave the app for Razorpay
  },
  error: (err) => {
    if (SEAT_ERRORS.has(apiError.code)) {
      // someone else took the seat meanwhile: refresh the seat map, go back to the seat step
    }
  },
});
```

Two production lessons:

- **Allow-list redirects.** `TRUSTED_PAYMENT_HOSTS` (from the environment files) lists the only
  hosts we'll send customers to. `isTrustedRedirect` requires HTTPS and an exact host or subdomain.
  `rzp.io.evil.com` does **not** pass, because of the leading-dot check.
- **Handle conflicts as a normal case.** Two people clicking the same seat at the same moment
  happens in real life. The app recovers gracefully instead of showing a generic error.

### Step 5: Back from payment (`features/payment/payment-return-page.ts`)

Razorpay redirects to `/booking-success/:bookingId?razorpay_payment_id=pay_...`. The page:

1. Validates the payment id format, then asks our server to **verify** it. The server double-checks
   with Razorpay, so editing the URL confirms nothing.
2. **Polls** `GET /api/bookings/:id` every 2 s, up to ~30 s, because confirmation travels
   asynchronously (payment-service -> Kafka -> booking-service).

```ts
const poll$ = timer(0, POLL_EVERY_MS).pipe(
  switchMap(() => this.bookingApi.get(bookingId)),
  takeWhile((b, attempt) => b.status === 'PENDING' && attempt < POLL_ATTEMPTS - 1, true),
  last(),
);
return concat(defer(() => verify$), poll$);
```

Read it as: "every 2 s fetch the booking; keep going while it's PENDING and we haven't hit the
limit; emit only the final answer; and do all that after verification". Even if the customer closes
the tab, Razorpay's webhook confirms the booking on the server. The UI is never the only path to a
correct result.

### Step 6: My trips (`features/trips/`)

Bookings grouped into upcoming / past / cancelled (`trip-groups.ts`), details with e-tickets,
"pay now" for unpaid bookings, cancelling unpaid ones, and print styles (`.no-print`).

---

## 15. Styling

- **Design tokens** (`src/styles/_tokens.scss`): every colour, spacing step and font size is a CSS
  variable (`--color-brand`, `--space-4`). Components use `var(--space-4)`, never raw `16px`.
  Change a token and the whole site follows. The colour pairs were checked for WCAG AA contrast.
- **Global styles** (`src/styles.scss` -> `_base.scss`, `_components.scss`, `_mixins.scss`):
  element defaults and shared classes like `.btn`, `.card`, `.field`, `.alert`, `.badge`. No UI
  library: the design system is small enough to read.
- **Component styles** (`*.scss` next to each component): scoped to that component.
- **SCSS** is CSS with variables, nesting and mixins, compiled to plain CSS at build time.
  `stylePreprocessorOptions.includePaths` in `angular.json` is why components can write
  `@use 'mixins'` without long relative paths.
- **Budgets** in `angular.json` fail the production build if the initial bundle exceeds 700 kB or a
  single component's CSS exceeds 10 kB. Performance regressions are caught in CI, not by users on
  slow phones.
- The font is **self-hosted** (`@fontsource-variable/source-sans-3`), not loaded from Google Fonts,
  so no third-party request and it works with the strict CSP.

---

## 16. Accessibility

Accessibility (a11y) means the site works for people using keyboards, screen readers, zoom, or
high contrast. It's a legal requirement in many places, and good for everyone. What this app does:

| Practice | Where |
|---|---|
| "Skip to main content" link | `app.html` |
| Focus moves to `<main>` after navigation, and to the step heading in checkout | `app.ts`, `checkout-page.ts` |
| Every input has a `<label for>`; errors linked with `aria-describedby` | all forms, `FieldError` |
| `role="status"` / `role="alert"` / `aria-live` so changes are announced | loading, errors, result count |
| `aria-expanded`, `aria-controls` on toggles | header menu, filters, "change search" |
| `aria-current="step"` in the checkout stepper | `checkout-page.html` |
| Combobox keyboard pattern | `airport-field.ts` |
| Decorative SVGs marked `aria-hidden="true"`; hidden text via `.visually-hidden` | everywhere |
| Lint rules that enforce template accessibility | `eslint.config.js` (`templateAccessibility`) |
| `lang="en-IN"` on `<html>` | `index.html` |

---

## 17. Security in the browser

Remember: everything in the browser is visible to, and changeable by, the user. The browser's job
is to **not make things worse**: don't leak data, don't send users to bad sites, don't run injected
code.

| Threat | Defence | Where |
|---|---|---|
| Secrets in the bundle | Environment files hold no secrets (comment says so) | `src/environments/` |
| Token leaking to third parties | Bearer header only for our own API | `authInterceptor`, `isApiRequest` |
| Open redirect after login | `safeReturnUrl` allows only internal paths | `core/util/safe-redirect.ts` |
| Fake payment page | `isTrustedRedirect` allow-list + HTTPS | `review-step.ts` |
| Cross-site scripting (XSS) | Angular escapes `{{ }}` output; strict **Content-Security-Policy** with a per-request nonce | templates, `nginx/security-headers.conf` |
| Clickjacking | `frame-ancestors 'none'`, `X-Frame-Options: DENY` | nginx |
| Account enumeration | One generic "wrong credentials" message | `login-page.ts` |
| Server internals leaking | 5xx messages replaced with friendly text | `api-error.ts` |
| Tampered bundles from a CDN | `subresourceIntegrity: true` | `angular.json` |
| Public source maps | Generated "hidden", deleted from the image, `.map` returns 404 | `Dockerfile`, nginx |

**The CSP nonce, explained.** A Content-Security-Policy tells the browser "only run scripts and
styles from these sources". Angular inserts some `<style>` tags at runtime, which a strict CSP
would block. So for every request nginx generates a random value (`$request_id`), puts it in the
CSP header (`'nonce-<value>'`), and replaces the text `CSP_NONCE` in `index.html` with the same
value. Angular reads `ngCspNonce` and tags its styles with it. An attacker can't guess the value,
so injected code still won't run.

---

## 18. Testing

Run with `npm test` (watch mode) or `npm run test:ci` (once, for CI). The runner is **Vitest** with
**jsdom** (a fake browser in Node), wired through Angular's `@angular/build:unit-test` builder.
Tests live next to the code as `*.spec.ts`. Test data factories are in `src/testing/fixtures.ts`.

### Three kinds of tests here

**1. Pure functions: the easiest and most valuable.** The project deliberately pulls logic out of
components into plain functions (`flight-filters.ts`, `search-criteria.ts`, `checkout-pricing.ts`,
`seat-layout.ts`, `safe-redirect.ts`, `jwt.ts`, `page.ts`), so it can be tested with no Angular at
all:

```ts
expect(isApiRequest('//cdn.example/x.js', '')).toBe(false);
```

**2. Services with fake HTTP.** `core/http/interceptors.spec.ts`:

```ts
TestBed.configureTestingModule({
  providers: [
    provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
    provideHttpClientTesting(),                                 // no real network
    { provide: AuthStore, useValue: { token: () => token, expire } }, // a fake, via DI
  ],
});
...
http.get('/api/bookings/user/history').subscribe();
const request = backend.expectOne('/api/bookings/user/history');
expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
request.flush([]);   // answer the request
```

`TestBed` is Angular's test version of the app config. This is where DI pays off: swapping the
real `AuthStore` for a fake is one line. `backend.verify()` in `afterEach` fails the test if a
request was left unanswered.

**3. Components through the DOM.** `features/auth/login-page.spec.ts` types into real inputs,
submits the real form, and checks what the user would see:

```ts
expect(fixture.nativeElement.textContent).toContain('Email is required');
```

Test **behaviour the user sees** ("an empty form shows errors and doesn't call login"), not
implementation details ("the `submitting` signal is false"). Tests written that way survive
refactoring.

`vi.fn()` creates a fake function that records its calls. `vi.spyOn(router, 'navigateByUrl')`
watches a real method.

---

## 19. Building and shipping to production

### `ng build`

`npm run build` uses the **production** configuration in `angular.json` (the default):

| Setting | What it does |
|---|---|
| `optimization` | Minifies JS/CSS, inlines critical CSS, removes unused code |
| `outputHashing: "all"` | File names contain a content hash: `main-AB12CD.js` |
| `budgets` | Fail the build if bundles grow too big |
| `sourceMap.hidden` | Maps for error trackers, not linked from the JS |
| `subresourceIntegrity` | The browser verifies each file wasn't altered |

The **development** configuration (`ng serve`) skips optimization for fast rebuilds and swaps
`environment.ts` for `environment.development.ts` (`fileReplacements`). Both files must match the
`AppEnvironment` interface, so a missing setting is a compile error.

### Docker: a multi-stage build (`frontend/Dockerfile`)

```
Stage 1 (node:24-alpine)          Stage 2 (nginx-unprivileged:alpine)
  npm ci                            copy nginx config
  npm run build                     copy dist/airline-web/browser  <- only the built files
  delete *.map files                runs as a non-root user on port 8080
                                    HEALTHCHECK hits /healthz
```

The final image contains no Node, no source code, no `node_modules`. It's smaller and has less
to attack. Copying `package*.json` before the rest of the source lets Docker cache the `npm ci`
layer between builds.

### nginx (`frontend/nginx/default.conf.template`)

| Block | Purpose |
|---|---|
| `/healthz` | Liveness probe for Docker/Kubernetes |
| `/api/`, `/auth/` -> `${API_GATEWAY_URL}` | Same-origin API: no CORS, the browser talks to one host. The URL comes from an env var, so one image works in every environment |
| `*.map` -> 404 | Source maps never public |
| Hashed assets: `Cache-Control: max-age=31536000, immutable` | Cache for a year (the name changes when content does); a missing file is a real 404, not `index.html` |
| Everything else -> `index.html`, `Cache-Control: no-cache` | SPA deep links work; users get new releases immediately |
| `sub_filter "CSP_NONCE" $request_id` | The per-request CSP nonce (section 17) |
| `security-headers.conf` included per `location` | In nginx, an `add_header` inside a `location` drops inherited headers, so the file is included everywhere |

HSTS (force HTTPS) is deliberately left to the TLS terminator in front of the container, as the
comment explains.

---

## 20. Recipe: adding a new page

Say you want `/offers`.

1. **Model**: add the JSON shape to `core/models/api-models.ts` (mirror the Java DTO, note its
   name in a comment).
2. **API**: add `core/api/offers-api.ts`, an `@Injectable({ providedIn: 'root' })` class with
   `inject(HttpClient)` and `inject(API_BASE_URL)`. HTTP only, no state.
3. **Logic**: put sorting/filtering/calculation in a plain `offers-*.ts` function file and unit-test
   it.
4. **Page**: `features/offers/offers-page.ts` + `.html` + `.scss`. Load with `rxResource`, render the
   four states with `app-loading` / `app-error-state` / `app-empty-state`.
5. **Route**: add it to `app.routes.ts` with `title`, `loadComponent`, and `canActivate:
   [requireSignIn]` if it needs an account. Keep `'**'` last.
6. **Link**: add a `routerLink` to `core/layout/site-header.html` with `routerLinkActive`.
7. **Check**: `npm run lint`, `npm test`, `npm run build` (budgets!), then click through it with the
   keyboard only.

---

## 21. What a production team would still add

The codebase is production-minded; these are the honest next steps a team would plan for.

- **Error monitoring.** Wire Sentry/Datadog into `GlobalErrorHandler` (the `TODO(monitoring)`) and
  upload the hidden source maps in the Docker build step.
- **Token storage.** The JWT is in `localStorage`, a documented trade-off forced by the backend
  returning the token in the body. The stronger pattern is an **httpOnly, Secure, SameSite cookie**
  set by the gateway (JavaScript can't read it, so XSS can't steal it), plus CSRF protection. That
  is a backend + gateway change first.
- **End-to-end tests.** Unit tests cover logic and components; a Playwright suite running the real
  booking journey against a seeded backend would catch integration breaks. Today that journey is a
  manual checklist ([FRONTEND_TESTING.md](FRONTEND_TESTING.md)).
- **CI pipeline** running `lint`, `test:ci` and `build` on every pull request.
- **Real-user performance metrics** (Core Web Vitals) and an analytics/consent setup, if the
  business needs them.
- **Internationalisation.** Text is English-only in templates. Angular's `i18n` tooling would be
  the route to more languages.

---

## 22. Glossary

| Term | Meaning |
|---|---|
| **SPA** | Single-page app: one HTML page, JavaScript redraws the screen |
| **Component** | A class + template + styles for one piece of UI |
| **Template** | A component's HTML with Angular syntax |
| **Selector** | The custom tag a component is used by (`app-flight-card`) |
| **Input / Output** | Data into a component / events out of it |
| **Signal** | A value that notifies readers when it changes (`signal`, `computed`, `effect`) |
| **Zoneless / OnPush** | Angular re-renders only when signals or inputs change |
| **Service** | A class for shared logic or state |
| **Dependency injection (DI)** | Classes ask for what they need with `inject()` instead of creating it |
| **Provider** | A rule telling DI what to give for a request (`{ provide, useClass }`) |
| **InjectionToken** | A key to inject plain values by |
| **Observable** | A stream of values over time (RxJS) |
| **Operator** | A function that transforms a stream (`map`, `switchMap`...) |
| **rxResource** | Loads async data driven by signals; exposes loading/error/value signals |
| **Pipe** | A display formatter in templates (`date`, `money`) |
| **Directive** | Adds behaviour to an existing element (`routerLink`, `formControlName`) |
| **Router / route** | Maps URLs to page components |
| **Lazy loading** | Downloading a page's code only when it is opened |
| **Guard** | A function that decides whether a route may open |
| **Interceptor** | A function every HTTP request passes through |
| **Reactive Forms** | Forms defined in TypeScript (`FormGroup`, `FormControl`, `FormArray`) |
| **ControlValueAccessor** | The contract that makes a custom component work as a form control |
| **JWT** | Signed token that proves who the user is |
| **CSP** | Content-Security-Policy: browser allow-list for scripts and styles |
| **Open redirect** | A bug that lets an attacker make your site send users elsewhere |
| **Idempotent** | Safe to repeat: doing it twice has the same effect as once (GET, not POST) |
| **TestBed** | Angular's test harness for configuring providers and creating components |
