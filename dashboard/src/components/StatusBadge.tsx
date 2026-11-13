/**
 * Coloured pill for a payment status.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import type { PaymentStatus } from '../types';

export default function StatusBadge({ status }: { status: PaymentStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>;
}
