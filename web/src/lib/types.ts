/**
 * Shared types for the website's UI renderings (they mirror the PayLedger API).
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
export type Status = 'CREATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'REVERSED';

export interface TimelineStep {
  status: Status;
  /** The reason text the real API writes into the payment timeline. */
  reason: string;
  /** Example IST time, hh:mm:ss */
  time: string;
}
