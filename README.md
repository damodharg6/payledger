# PayLedger

[![CI](https://github.com/damodharg6/payledger/actions/workflows/ci.yml/badge.svg)](https://github.com/damodharg6/payledger/actions/workflows/ci.yml)
[![Website](https://github.com/damodharg6/payledger/actions/workflows/pages.yml/badge.svg)](https://damodharg6.github.io/payledger/)

**Live site:** https://damodharg6.github.io/payledger/ · **API docs:** https://damodharg6.github.io/payledger/developers/

**Every rupee. Accounted for.**

A payment and double-entry ledger platform for merchants, built with Java 21 and Spring Boot 4.1.
No customer is charged twice, no rupee is lost, and failed payments are reversed within T+1
(the RBI turnaround rule for "money debited, payment failed").

> An engineering portfolio project by **Giddaluru Damodhar (MrDamodar), Agentic AI Engineer**.
> It is not a licensed payment service and moves no real money.

---

## What it guarantees

| Promise | How |
|---|---|
| **Charged once** | `POST /api/v1/payments` requires an `Idempotency-Key`. The key is claimed with a database primary key (`INSERT … ON CONFLICT DO NOTHING`), so a retry returns the stored result and a concurrent duplicate gets `409`. Same key with a different body gets `422`. |
| **No lost money** | A double-entry ledger: every transaction's debits equal its credits, entries are append-only (a database trigger rejects `UPDATE` and `DELETE`), and mistakes are fixed with reversing entries. Accounts are locked with `SELECT … FOR UPDATE` in a fixed order, so concurrent postings cannot deadlock. |
| **Reversed within T+1** | If the bank debits the customer but the payment fails, the money waits in a clearing account with a T+1 deadline (IST calendar days). A scheduled job using `FOR UPDATE SKIP LOCKED` returns it, safely across many app instances. |
| **Timeouts are never guessed** | A bank call that times out is followed by a status inquiry. An unknown outcome stays `PROCESSING` until the bank knows, so it is never marked failed by mistake. |

Payment states: `CREATED → PROCESSING → SUCCEEDED | FAILED`, and `FAILED → REVERSED`. Nothing else is allowed.

## Measured, not claimed

All numbers come from the project's own test suite (`./mvnw verify`):

| Test | Result |
|---|---|
| 50 threads send the same payment with the same `Idempotency-Key` at once | **1** payment, **1** bank charge request, **1** ledger posting |
| 1,000 payments from 25 concurrent clients; bank set to 20% failures and 10% timeouts; every 10th request retried; 100 refunds | **0** double charges, **0** paise lost, every failed debit reversed (116 of 116), **0** late reversals, total debits = total credits (₹5,28,777.50) |
| 40 threads moving money in opposite directions between the same two accounts | **0** deadlocks (without the fixed lock order: `deadlock detected` in 3 of 3 runs) |
| 4 parallel reversal workers draining 20 reversals | split 5 / 5 / 5 / 5, no payment reversed twice |
| Automated tests | **188** backend + **14** dashboard, all passing |
| Coverage (JaCoCo) | **96.1%** lines, 86.0% branches; the build fails below 80% overall or 70% per service |

## Run it

You need Docker. Clone the repository, then one command starts PostgreSQL, the API and the dashboard:

```bash
git clone https://github.com/damodharg6/payledger.git
cd payledger
docker compose up --build -d
```

| What | Where |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Merchant dashboard | http://localhost:3000 |
| Health | http://localhost:8080/actuator/health |

Load a demo merchant (with a dashboard login) and 20 payments covering every bank scenario:

```bash
scripts/seed-demo.sh
```

### Develop locally

- **API:** start only the database with `docker compose up -d postgres` (host port 15432), then run `PayLedgerApplication` from your IDE with JDK 21, or `./mvnw spring-boot:run`.
- **Dashboard:** `npm --prefix dashboard install` then `npm --prefix dashboard run dev` (http://localhost:5173).
- **Website:** `npm --prefix web install` then `npm --prefix web run dev` (http://localhost:5180). Every push to `main` that changes `web/` redeploys the live site through `.github/workflows/pages.yml`.
- **Tests:** `./mvnw verify` (needs Docker for Testcontainers) and `npm --prefix dashboard test`.

## A first request

```bash
# 1. Register a merchant (the API key is shown once)
curl -X POST http://localhost:8080/api/v1/merchants \
  -H "Content-Type: application/json" \
  -d '{"name": "Chai Point", "email": "owner@chaipoint.example", "dashboardPassword": "a-long-dashboard-password"}'

# 2. Create a payment (amounts are integer paise: 12500000 = ₹1,25,000.00)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-API-Key: $PAYLEDGER_API_KEY" \
  -H "Idempotency-Key: order-1001" \
  -H "Content-Type: application/json" \
  -d '{"amountPaise": 12500000, "customerRef": "cust-42"}'
```

To force a bank outcome while testing, start `customerRef` with `sim_success`, `sim_decline`,
`sim_debit_fail`, `sim_timeout_success`, `sim_timeout_pending` or `sim_bank_down`.

## API

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/merchants` | Register a merchant; returns the API key once |
| `POST /api/v1/auth/login` | Dashboard login; returns a 1-hour JWT |
| `POST /api/v1/payments` | Create and process a payment (`Idempotency-Key` required) |
| `GET /api/v1/payments/{id}` | One payment with its status timeline |
| `GET /api/v1/payments` | List, filtered by status and date, paginated |
| `POST /api/v1/payments/{id}/refunds` | Full or partial refund, never more than the payment |
| `GET /api/v1/ledger/accounts/{id}/balance` | Cached balance and the balance recomputed from entries |
| `GET /api/v1/ledger/accounts/{id}/entries` | Ledger entries, newest first |

Merchant servers authenticate with `X-API-Key` (stored only as a SHA-256 hash). Dashboard users
authenticate with a JWT (passwords stored with BCrypt). Errors are RFC 7807 `application/problem+json`.

## Tech stack

- **Backend:** Java 21 (virtual threads), Spring Boot 4.1, Spring Security 7 (API keys + JWT), Spring Data JPA, PostgreSQL 16, Flyway, springdoc-openapi
- **Tests:** JUnit 5, Mockito, Testcontainers, JaCoCo
- **Dashboard:** React 19, TypeScript, Vite, Vitest
- **Website:** React 19, Vite, Tailwind CSS v4, Motion, pre-rendered (Lighthouse 100 on Performance, Accessibility, Best Practices and SEO)
- **Delivery:** multi-stage Docker images, docker compose, GitHub Actions CI, website on GitHub Pages

## Project layout

```
src/main/java/com/payledger/
  payment/    API, state machine, idempotency, processing, refunds, T+1 reversal job
  ledger/     double-entry postings, account locking, balances
  merchant/   registration, hashed API keys, dashboard users, login
  bank/       mock bank / UPI simulator and the timeout gateway
  common/     security, errors (RFC 7807), configuration
src/main/resources/db/migration/   Flyway migrations V1–V5
dashboard/   merchant dashboard (React)
web/         public website and developer docs (React)
scripts/     demo data
```

## Roadmap

- **v1 (now):** everything above.
- **v2:** Kafka with a transactional outbox, saga, signed webhooks, Resilience4j circuit breakers, rate limiting, chaos tests.
- **v3:** Kubernetes, OpenTelemetry tracing, Grafana dashboards, k6 load tests, nightly reconciliation against bank files.

## Author

Designed and built by **Giddaluru Damodhar (MrDamodar), Agentic AI Engineer**.

Copyright © 2026 Giddaluru Damodhar. All rights reserved.
