/**
 * Spring Data returns lists in pages. Depending on the Spring version and its settings, the
 * page metadata comes in one of two JSON shapes:
 *
 *   newer ("VIA_DTO"):  { "content": [...], "page": { "number": 0, "size": 20, "totalElements": 3, "totalPages": 1 } }
 *   older (PageImpl):   { "content": [...], "number": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
 *
 * `toPage()` accepts either and gives the rest of the app ONE simple shape to work with.
 */
export interface Page<T> {
  readonly items: readonly T[];
  readonly pageNumber: number;
  readonly pageSize: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

interface PageMetadata {
  number?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface SpringPage<T> extends PageMetadata {
  content?: T[];
  page?: PageMetadata;
}

export function toPage<T>(raw: SpringPage<T> | null | undefined): Page<T> {
  const items = raw?.content ?? [];
  const meta: PageMetadata = raw?.page ?? raw ?? {};
  return {
    items,
    pageNumber: meta.number ?? 0,
    pageSize: meta.size ?? items.length,
    totalItems: meta.totalElements ?? items.length,
    totalPages: meta.totalPages ?? (items.length > 0 ? 1 : 0),
  };
}
