import { addDaysIso, todayIso } from '../../shared/validators';
import { parseCriteria, toQueryParams } from './search-criteria';

describe('search criteria in the URL', () => {
  const tomorrow = addDaysIso(todayIso(), 1);

  it('round-trips through query params', () => {
    const criteria = {
      from: 'BOM',
      to: 'DEL',
      date: tomorrow,
      passengers: 2,
      cabin: 'BUSINESS' as const,
    };
    const params = toQueryParams(criteria);
    expect(parseCriteria(params)).toEqual(criteria);
  });

  it('normalises case and defaults cabin and passengers', () => {
    expect(parseCriteria({ from: 'bom', to: 'del', date: tomorrow })).toEqual({
      from: 'BOM',
      to: 'DEL',
      date: tomorrow,
      passengers: 1,
      cabin: 'ECONOMY',
    });
  });

  it.each([
    ['same airports', { from: 'BOM', to: 'BOM', date: tomorrow }],
    ['bad airport code', { from: 'BOMBAY', to: 'DEL', date: tomorrow }],
    ['date in the past', { from: 'BOM', to: 'DEL', date: '2001-01-01' }],
    ['not a date', { from: 'BOM', to: 'DEL', date: 'soon' }],
    ['too many passengers', { from: 'BOM', to: 'DEL', date: tomorrow, pax: '10' }],
    ['unknown cabin', { from: 'BOM', to: 'DEL', date: tomorrow, cabin: 'CARGO' }],
  ])('rejects %s', (_label, raw) => {
    expect(parseCriteria(raw)).toBeNull();
  });
});
