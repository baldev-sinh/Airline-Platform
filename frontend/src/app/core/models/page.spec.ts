import { toPage } from './page';

describe('toPage', () => {
  it('reads the newer Spring format with a nested "page" object', () => {
    const page = toPage({
      content: ['a', 'b'],
      page: { number: 1, size: 2, totalElements: 5, totalPages: 3 },
    });
    expect(page).toEqual({
      items: ['a', 'b'],
      pageNumber: 1,
      pageSize: 2,
      totalItems: 5,
      totalPages: 3,
    });
  });

  it('reads the older flat Spring format', () => {
    const page = toPage({ content: ['a'], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    expect(page.totalItems).toBe(1);
    expect(page.pageSize).toBe(20);
  });

  it('survives an empty or missing body', () => {
    expect(toPage(null).items).toEqual([]);
    expect(toPage({}).totalPages).toBe(0);
  });
});
