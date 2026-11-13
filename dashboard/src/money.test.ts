/**
 * Money helpers never use floating point and follow Indian digit grouping.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { describe, expect, it } from 'vitest';
import { formatPaise, paiseToInput, parseRupeesToPaise } from './money';

describe('formatPaise', () => {
  it('uses lakh/crore grouping and always two decimals', () => {
    expect(formatPaise(12_345_678)).toBe('₹1,23,456.78');
    expect(formatPaise(1_000_000_000)).toBe('₹1,00,00,000.00');
    expect(formatPaise(5)).toBe('₹0.05');
    expect(formatPaise(0)).toBe('₹0.00');
    expect(formatPaise(-50_000)).toBe('-₹500.00');
  });

  it('rejects non-integer paise', () => {
    expect(() => formatPaise(10.5)).toThrow();
  });
});

describe('parseRupeesToPaise', () => {
  it('parses rupees with up to two decimals exactly', () => {
    expect(parseRupeesToPaise('500')).toBe(50_000);
    expect(parseRupeesToPaise('500.7')).toBe(50_070);
    expect(parseRupeesToPaise('500.75')).toBe(50_075);
    expect(parseRupeesToPaise('₹1,23,456.78')).toBe(12_345_678);
    expect(parseRupeesToPaise(' 0.01 ')).toBe(1);
  });

  it('is exact where floating point is not (0.29 * 100 = 28.999999999999996)', () => {
    expect(parseRupeesToPaise('0.29')).toBe(29);
    expect(parseRupeesToPaise('1.15')).toBe(115);
  });

  it('refuses anything that is not a positive amount with at most 2 decimals', () => {
    for (const bad of ['', 'abc', '0', '0.00', '-5', '1.234', '1e3', '.5', '1.']) {
      expect(parseRupeesToPaise(bad), bad).toBeNull();
    }
  });

  it('round-trips with paiseToInput', () => {
    for (const p of [1, 99, 100, 50_075, 12_345_678]) {
      expect(parseRupeesToPaise(paiseToInput(p))).toBe(p);
    }
  });
});
