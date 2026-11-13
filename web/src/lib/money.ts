/**
 * Indian money format from integer paise: 12500000 → "₹1,25,000.00". Integer maths only.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
const GROUPING = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

export function inr(paise: number): string {
  const sign = paise < 0 ? '-' : '';
  const abs = Math.abs(Math.round(paise));
  return `${sign}₹${GROUPING.format(Math.floor(abs / 100))}.${String(abs % 100).padStart(2, '0')}`;
}

/** 202 → "202", 1000 → "1,000" (Indian grouping for counts too). */
export function count(n: number): string {
  return GROUPING.format(Math.round(n));
}
