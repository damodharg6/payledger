/**
 * Date helpers. The API speaks UTC instants; people read Indian Standard Time.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */

const IST_DATE_TIME = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

export function formatIst(instant: string | null): string {
  return instant ? `${IST_DATE_TIME.format(new Date(instant))} IST` : '—';
}

/** "2026-11-13" (a calendar day in IST) → the UTC instant when that day starts in India. */
export function istDayStart(day: string): string {
  return new Date(`${day}T00:00:00+05:30`).toISOString();
}

/** The instant the day after `day` starts in IST: an exclusive upper bound for "to". */
export function istDayEnd(day: string): string {
  const start = new Date(`${day}T00:00:00+05:30`);
  return new Date(start.getTime() + 24 * 60 * 60 * 1000).toISOString();
}

export function shortId(id: string): string {
  return id.slice(0, 8);
}
