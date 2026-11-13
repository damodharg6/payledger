/**
 * Money helpers. Amounts are integer paise everywhere, the same rule as the backend:
 * formatting and parsing use integer and string arithmetic only, never floating point, so
 * "₹0.10 + ₹0.20" can never become ₹0.30000000000000004.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */

const GROUPING = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

/** 12345678 → "₹1,23,456.78" (Indian digit grouping: lakh and crore). */
export function formatPaise(paise: number): string {
  if (!Number.isSafeInteger(paise)) throw new Error(`Not an integer amount of paise: ${paise}`);
  const sign = paise < 0 ? '-' : '';
  const abs = Math.abs(paise);
  const rupees = Math.floor(abs / 100);
  const rest = abs % 100;
  return `${sign}₹${GROUPING.format(rupees)}.${String(rest).padStart(2, '0')}`;
}

/**
 * "1,234.5" → 123450 paise. Accepts at most 2 decimal places and positive amounts only.
 * Returns null for anything else (e.g. "12.345", "-5", "abc", "0").
 */
export function parseRupeesToPaise(input: string): number | null {
  const cleaned = input.trim().replace(/[,₹\s]/g, '');
  const match = /^(\d{1,10})(?:\.(\d{1,2}))?$/.exec(cleaned);
  if (!match) return null;
  const rupees = Number(match[1]);
  const paise = Number((match[2] ?? '').padEnd(2, '0'));
  const total = rupees * 100 + paise;
  return total > 0 && Number.isSafeInteger(total) ? total : null;
}

/** 123450 → "1234.50", for pre-filling an input box. */
export function paiseToInput(paise: number): string {
  return `${Math.floor(paise / 100)}.${String(paise % 100).padStart(2, '0')}`;
}
