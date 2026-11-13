/**
 * IST date bounds for the list filter, and the hash router.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { describe, expect, it } from 'vitest';
import { formatIst, istDayEnd, istDayStart } from './format';
import { parseRoute } from './router';

describe('IST day bounds', () => {
  it('a day in India starts at 18:30 UTC the evening before', () => {
    expect(istDayStart('2026-11-13')).toBe('2026-11-12T18:30:00.000Z');
    expect(istDayEnd('2026-11-13')).toBe('2026-11-13T18:30:00.000Z');
  });

  it('formats instants in IST', () => {
    expect(formatIst('2026-11-13T18:20:00Z')).toContain('23:50:00');
    expect(formatIst(null)).toBe('—');
  });
});

describe('parseRoute', () => {
  it('recognises a payment page and falls back to the list', () => {
    const id = '0d08e96d-e423-4af7-ba64-98425773d0b9';
    expect(parseRoute(`#/payments/${id}`)).toEqual({ name: 'payment', id });
    expect(parseRoute('#/payments')).toEqual({ name: 'payments' });
    expect(parseRoute('#/payments/not-an-id')).toEqual({ name: 'payments' });
    expect(parseRoute('')).toEqual({ name: 'payments' });
  });
});
