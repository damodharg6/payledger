/**
 * Vertical status timeline of one payment (CREATED → PROCESSING → …), oldest first.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { formatIst } from '../format';
import type { TimelineEntry } from '../types';
import StatusBadge from './StatusBadge';

export default function Timeline({ entries }: { entries: TimelineEntry[] }) {
  return (
    <ol className="timeline">
      {entries.map((e, i) => (
        <li key={`${e.to}-${i}`} className={`step step-${e.to.toLowerCase()}`}>
          <span className="dot" aria-hidden />
          <div>
            <StatusBadge status={e.to} />
            <span className="muted step-time">{formatIst(e.at)}</span>
            {e.reason && <p className="step-reason">{e.reason}</p>}
          </div>
        </li>
      ))}
    </ol>
  );
}
