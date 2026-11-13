/**
 * The API client sends the JWT and Idempotency-Key, and turns RFC 7807 problems into ApiError.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, listPayments, refundPayment, setToken, setUnauthorizedHandler } from './api';

type Call = { url: string; init: RequestInit };
let calls: Call[];

function respond(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  }));
}

beforeEach(() => {
  calls = [];
  setToken('jwt-123');
});

afterEach(() => {
  vi.unstubAllGlobals();
  setToken(null);
});

describe('api client', () => {
  it('sends the bearer token and builds the list query', async () => {
    respond(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await listPayments({ status: 'FAILED', from: '2026-11-12T18:30:00.000Z', page: 2, size: 20 });

    expect(calls[0].url).toBe('/api/v1/payments?page=2&size=20&status=FAILED&from=2026-11-12T18%3A30%3A00.000Z');
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBe('Bearer jwt-123');
  });

  it('sends the idempotency key with a refund', async () => {
    respond(201, { id: 'r1', refundablePaise: 0 });
    await refundPayment('p1', 500, '  ', 'key-abc');

    const headers = calls[0].init.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe('key-abc');
    expect(JSON.parse(calls[0].init.body as string)).toEqual({ amountPaise: 500 });
  });

  it('turns a problem response into ApiError with the detail', async () => {
    respond(422, { title: 'Unprocessable request', status: 422, detail: 'Refund of 9 paise exceeds the refundable amount of 5 paise' });
    const err = await refundPayment('p1', 9, '', 'k').catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.message).toContain('exceeds the refundable amount');
  });

  it('lists field errors from validation problems', async () => {
    respond(400, { title: 'Validation failed', status: 400, errors: { amountPaise: 'must be greater than 0' } });
    const err = await refundPayment('p1', 0, '', 'k').catch((e) => e);
    expect(err.message).toBe('amountPaise: must be greater than 0');
  });

  it('signs out when the token is rejected', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    respond(401, { title: 'Unauthorized', status: 401 });

    await expect(listPayments({ page: 0, size: 20 })).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });
});
