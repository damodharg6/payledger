/**
 * Shapes of the PayLedger API responses (mirrors the Java record DTOs).
 * All money values are integer paise.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */

export type PaymentStatus = 'CREATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'REVERSED';

export const PAYMENT_STATUSES: PaymentStatus[] = ['CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'REVERSED'];

export type Role = 'ADMIN' | 'VIEWER';

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  merchantId: string;
  merchantName: string;
  email: string;
  role: Role;
}

export interface TimelineEntry {
  from: PaymentStatus | null;
  to: PaymentStatus;
  reason: string | null;
  at: string;
}

export interface Payment {
  id: string;
  merchantId: string;
  amountPaise: number;
  currency: string;
  status: PaymentStatus;
  customerRef: string;
  bankReference: string | null;
  failureReason: string | null;
  reverseBy: string | null;
  createdAt: string;
  updatedAt: string;
  refundedPaise: number;
  timeline: TimelineEntry[];
}

export interface PaymentSummary {
  id: string;
  amountPaise: number;
  currency: string;
  status: PaymentStatus;
  customerRef: string;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Refund {
  id: string;
  paymentId: string;
  amountPaise: number;
  reason: string | null;
  createdAt: string;
  totalRefundedPaise: number;
  refundablePaise: number;
}

/** RFC 7807 problem body returned by every API error. */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}
