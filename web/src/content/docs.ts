/**
 * Code samples for the /developers page. Endpoints, headers, fields, status codes and error
 * messages match the PayLedger v1 API exactly.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import type { Sample } from './code';

export const START: Sample[] = [{
  label: 'Terminal',
  lang: 'bash',
  code: `docker compose up --build -d
# API          http://localhost:8080
# Dashboard    http://localhost:3000
# Swagger UI   http://localhost:8080/swagger-ui.html`,
}];

export const REGISTER: Sample[] = [{
  label: 'cURL',
  lang: 'bash',
  code: `curl -X POST http://localhost:8080/api/v1/merchants \\
  -H "Content-Type: application/json" \\
  -d '{"name": "Chai Point", "email": "owner@chaipoint.example",
       "dashboardPassword": "a-long-dashboard-password"}'`,
}];

export const REGISTER_RESPONSE: Sample = {
  label: '201 Created',
  lang: 'json',
  code: `{
  "merchantId": "c50b2207-0b35-4635-8653-5bd9942f0a1d",
  "name": "Chai Point",
  "email": "owner@chaipoint.example",
  "apiKey": "plk_3vV5XkR0dA7mJq2LxT8uYb1cE6nW9sPzHf4gK0oQ9wE",
  "ledgerAccountId": "a2d7c9e1-4f60-4b8e-8d1a-6c3f5e9b2a10",
  "createdAt": "2026-11-13T15:30:02.118Z"
}`,
};

export const LOGIN: Sample[] = [{
  label: 'cURL',
  lang: 'bash',
  code: `curl -X POST http://localhost:8080/api/v1/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"email": "owner@chaipoint.example", "password": "a-long-dashboard-password"}'`,
}];

export const LOGIN_RESPONSE: Sample = {
  label: '200 OK',
  lang: 'json',
  code: `{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi…",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "merchantId": "c50b2207-0b35-4635-8653-5bd9942f0a1d",
  "merchantName": "Chai Point",
  "email": "owner@chaipoint.example",
  "role": "ADMIN"
}`,
};

export const REFUND: Sample[] = [{
  label: 'cURL',
  lang: 'bash',
  code: `curl -X POST http://localhost:8080/api/v1/payments/$PAYMENT_ID/refunds \\
  -H "X-API-Key: $PAYLEDGER_API_KEY" \\
  -H "Idempotency-Key: refund-1001-a" \\
  -H "Content-Type: application/json" \\
  -d '{"amountPaise": 100050, "reason": "Damaged in transit"}'`,
}];

export const REFUND_RESPONSE: Sample = {
  label: '201 Created',
  lang: 'json',
  code: `{
  "id": "e41a9f2c-7d3b-4c51-9a0e-5b8d2f6c1e73",
  "paymentId": "7f3a9c2e-5b1d-4e8a-9c40-2d61f0b8a913",
  "amountPaise": 100050,
  "reason": "Damaged in transit",
  "totalRefundedPaise": 100050,
  "refundablePaise": 12399950
}`,
};

export const ERROR_422: Sample[] = [{
  label: '422 Unprocessable Content',
  lang: 'json',
  code: `{
  "type": "about:blank",
  "title": "Unprocessable request",
  "status": 422,
  "detail": "Refund of 500000 paise exceeds the refundable amount of 200000 paise",
  "instance": "/api/v1/payments/7f3a9c2e-5b1d-4e8a-9c40-2d61f0b8a913/refunds"
}`,
}];

export const ERROR_400: Sample[] = [{
  label: '400 Bad Request',
  lang: 'json',
  code: `{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "errors": {
    "amountPaise": "must be greater than 0",
    "customerRef": "must not be blank"
  }
}`,
}];
