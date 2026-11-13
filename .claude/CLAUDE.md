# PayLedger: notes for Claude Code

## What this project is
A payment and ledger platform for merchants, built as a portfolio project for Java backend and full-stack interviews.
Goal of v1 (ship by 30 Nov 2026): no double charges, no lost money, failed payments auto-reversed within T+1 (RBI rule).

## Stack
- Java 21, Spring Boot 4.1 (Maven), PostgreSQL 16 via docker-compose, Flyway migrations
- IDE: IntelliJ IDEA
- Later: React dashboard, Kafka, Redis, Resilience4j, Kubernetes (v2 and v3, not now)

## Rules
- Money is always `long` paise (BIGINT in the database). Never float, double or BigDecimal for stored amounts.
- The schema is owned by Flyway (`src/main/resources/db/migration`). Never change an applied migration; add a new `V<n>__*.sql`.
- `ledger_entries` is append-only (a trigger blocks UPDATE and DELETE). Fix mistakes with new reversing entries.
- Every ledger transaction must have total debits equal to total credits; check this in code and in tests.
- Payment status changes only through the state machine: CREATED → PROCESSING → SUCCEEDED or FAILED; FAILED → REVERSED.
- `POST /api/v1/payments` requires an `Idempotency-Key` header. Same key + same body returns the stored result; same key + different body returns 422.
- Use constructor injection, records for DTOs, `@Transactional` in services only, a global `@RestControllerAdvice` for errors.
- Package by feature: `payment`, `ledger`, `merchant`, `bank`, `common`.
- Every class Javadoc credits: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer.
- Write tests for every service: unit tests with JUnit 5 and Mockito; integration tests with Testcontainers PostgreSQL.
- Keep v1 a single Spring Boot app. Do not add Kafka, microservices or Kubernetes until v2.

## Run
- `docker compose up -d` then run `PayLedgerApplication` (or `mvn spring-boot:run`)
- Health: http://localhost:8080/actuator/health
