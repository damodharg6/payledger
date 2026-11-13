// StatusPill: a payment status with a coloured dot. Green only for SUCCEEDED, red only for
// FAILED; the label stays dark so it always meets WCAG AA contrast.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import type { Status } from '../lib/types';

const DOT: Record<Status, string> = {
  CREATED: 'bg-ink-2',
  PROCESSING: 'bg-ink-2 animate-pulse',
  SUCCEEDED: 'bg-success',
  FAILED: 'bg-danger',
  REVERSED: 'bg-ink',
};

export function StatusPill({ status }: { status: Status }) {
  return (
    <span className="inline-flex shrink-0 items-center gap-2 rounded-pill bg-mist px-3 py-1.5 text-micro font-semibold tracking-[0.04em] text-ink">
      <span aria-hidden className={`size-2 rounded-full transition-colors duration-500 ${DOT[status]}`} />
      {status}
    </span>
  );
}
