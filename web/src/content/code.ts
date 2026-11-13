/**
 * Code samples for the site. They use the real endpoints, headers and field names of the
 * PayLedger API; the response is the real response shape.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import type { Lang } from '../lib/highlight';

export interface Sample {
  label: string;
  lang: Lang;
  code: string;
}

export const CREATE_PAYMENT: Sample[] = [
  {
    label: 'cURL',
    lang: 'bash',
    code: `curl -X POST http://localhost:8080/api/v1/payments \\
  -H "X-API-Key: $PAYLEDGER_API_KEY" \\
  -H "Idempotency-Key: order-1001" \\
  -H "Content-Type: application/json" \\
  -d '{"amountPaise": 12500000, "customerRef": "cust-42"}'`,
  },
  {
    label: 'Java',
    lang: 'java',
    code: `var client = HttpClient.newHttpClient();
var request = HttpRequest.newBuilder(URI.create("http://localhost:8080/api/v1/payments"))
    .header("X-API-Key", System.getenv("PAYLEDGER_API_KEY"))
    .header("Idempotency-Key", "order-1001")
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(
        "{\\"amountPaise\\": 12500000, \\"customerRef\\": \\"cust-42\\"}"))
    .build();

var response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.statusCode() + " " + response.body());`,
  },
  {
    label: 'JavaScript',
    lang: 'js',
    code: `const res = await fetch("http://localhost:8080/api/v1/payments", {
  method: "POST",
  headers: {
    "X-API-Key": process.env.PAYLEDGER_API_KEY,
    "Idempotency-Key": "order-1001",
    "Content-Type": "application/json",
  },
  body: JSON.stringify({ amountPaise: 12500000, customerRef: "cust-42" }),
});

console.log(res.status, await res.json());`,
  },
];

/** The real response, with each timeline entry trimmed to "to" and "reason" so it fits. */
export const PAYMENT_RESPONSE: Sample = {
  label: '201 Created · abridged',
  lang: 'json',
  code: `{
  "id": "7f3a9c2e-5b1d-4e8a-9c40-2d61f0b8a913",
  "amountPaise": 12500000,
  "currency": "INR",
  "status": "SUCCEEDED",
  "customerRef": "cust-42",
  "bankReference": "MOCK-7f3a9c2e",
  "failureReason": null,
  "reverseBy": null,
  "refundedPaise": 0,
  "createdAt": "2026-11-13T15:33:27.412Z",
  "timeline": [
    { "to": "CREATED", "reason": "Payment created" },
    { "to": "PROCESSING", "reason": "Sent to bank" },
    { "to": "SUCCEEDED", "reason": "Bank confirmed payment" }
  ]
}`,
};
