/**
 * Small typed client for the PayLedger API. Every call goes to /api/v1 on the same origin
 * (the Vite dev server or nginx proxies it to Spring Boot) with the dashboard JWT attached.
 * Errors come back as RFC 7807 problems and are thrown as {@link ApiError}.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import type { LoginResponse, Page, Payment, PaymentStatus, PaymentSummary, Problem, Refund } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | null;

  constructor(status: number, message: string, problem: Problem | null) {
    super(message);
    this.status = status;
    this.problem = problem;
  }
}

let token: string | null = null;
let onUnauthorized: () => void = () => {};

export function setToken(value: string | null): void {
  token = value;
}

/** Called when the API says our token is no longer valid (expired or revoked). */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

async function request<T>(path: string, init: { method?: string; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...init.headers };
  if (init.body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`/api/v1${path}`, {
    method: init.method ?? 'GET',
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });

  if (!res.ok) {
    const problem = (await res.json().catch(() => null)) as Problem | null;
    if (res.status === 401 && token) onUnauthorized();
    const fieldErrors = problem?.errors ? Object.entries(problem.errors).map(([f, m]) => `${f}: ${m}`).join(', ') : '';
    throw new ApiError(res.status, fieldErrors || problem?.detail || res.statusText || `HTTP ${res.status}`, problem);
  }
  return (await res.json()) as T;
}

export function login(email: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/auth/login', { method: 'POST', body: { email, password } });
}

export interface PaymentFilter {
  status?: PaymentStatus;
  /** ISO instant, inclusive */
  from?: string;
  /** ISO instant, exclusive */
  to?: string;
  page: number;
  size: number;
}

export function listPayments(filter: PaymentFilter): Promise<Page<PaymentSummary>> {
  const q = new URLSearchParams({ page: String(filter.page), size: String(filter.size) });
  if (filter.status) q.set('status', filter.status);
  if (filter.from) q.set('from', filter.from);
  if (filter.to) q.set('to', filter.to);
  return request<Page<PaymentSummary>>(`/payments?${q.toString()}`);
}

export function getPayment(id: string): Promise<Payment> {
  return request<Payment>(`/payments/${encodeURIComponent(id)}`);
}

/** The idempotency key makes a retried click safe: the server refunds at most once per key. */
export function refundPayment(id: string, amountPaise: number, reason: string, idempotencyKey: string): Promise<Refund> {
  return request<Refund>(`/payments/${encodeURIComponent(id)}/refunds`, {
    method: 'POST',
    body: { amountPaise, reason: reason.trim() || undefined },
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}
