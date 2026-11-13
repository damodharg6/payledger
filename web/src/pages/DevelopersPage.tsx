// /developers: docs-style page with a sticky sidebar (scroll-spy) and the shared CodePanel.
// Every endpoint, field, status and message here matches the PayLedger v1 API.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { useEffect, useState, type ReactNode } from 'react';
import { CodePanel } from '../components/CodePanel';
import { CREATE_PAYMENT, PAYMENT_RESPONSE } from '../content/code';
import {
  ERROR_400, ERROR_422, LOGIN, LOGIN_RESPONSE, REFUND, REFUND_RESPONSE, REGISTER, REGISTER_RESPONSE, START,
} from '../content/docs';

const SECTIONS = [
  { id: 'quickstart', label: 'Quickstart' },
  { id: 'authentication', label: 'Authentication' },
  { id: 'payments', label: 'Payments' },
  { id: 'idempotency', label: 'Idempotency' },
  { id: 'webhooks', label: 'Webhooks' },
  { id: 'errors', label: 'Errors' },
] as const;

function C({ children }: { children: ReactNode }) {
  return <code className="rounded-md bg-mist px-1.5 py-0.5 font-mono text-[0.88em] text-ink">{children}</code>;
}

function Table({ head, rows, label }: { head: string[]; rows: ReactNode[][]; label: string }) {
  return (
    <div className="mt-5 overflow-x-auto rounded-tile ring-1 ring-hairline" tabIndex={0} role="region" aria-label={label}>
      <table className="w-full min-w-[34rem] border-collapse text-left text-small">
        <thead className="bg-mist">
          <tr>{head.map((h) => <th key={h} scope="col" className="px-4 py-3 font-semibold text-ink">{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-hairline align-top">
              {r.map((cell, j) => <td key={j} className="px-4 py-3 text-ink-2 first:text-ink">{cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Doc({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-title`} className="scroll-mt-24 border-t border-hairline pt-12 first:border-t-0 first:pt-0 md:pt-16">
      <h2 id={`${id}-title`} className="text-title">{title}</h2>
      <div className="mt-4 space-y-4 text-body text-ink-2 [&_h3]:mt-10 [&_h3]:text-body [&_h3]:font-semibold [&_h3]:text-ink">
        {children}
      </div>
    </section>
  );
}

function useActiveSection(): string {
  const [active, setActive] = useState<string>(SECTIONS[0].id);
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: '-20% 0px -65% 0px' },
    );
    SECTIONS.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, []);
  return active;
}

function Sidebar() {
  const active = useActiveSection();
  return (
    <nav aria-label="On this page" className="lg:sticky lg:top-[calc(var(--spacing-nav)+2.5rem)] lg:self-start">
      <p className="mb-3 text-micro font-semibold uppercase tracking-[0.06em] text-ink-2">On this page</p>
      <ul className="flex flex-wrap gap-2 lg:flex-col lg:gap-0.5">
        {SECTIONS.map((s) => (
          <li key={s.id}>
            <a href={`#${s.id}`} aria-current={active === s.id ? 'location' : undefined}
               className="block rounded-pill bg-mist px-3 py-1.5 text-small text-ink-2 transition-colors hover:text-ink aria-[current=location]:bg-ink aria-[current=location]:text-white lg:rounded-control lg:bg-transparent lg:px-3 lg:py-2 lg:aria-[current=location]:bg-mist lg:aria-[current=location]:font-semibold lg:aria-[current=location]:text-ink">
              {s.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export function DevelopersPage() {
  return (
    <div className="bg-white px-gutter pb-section pt-[clamp(3.5rem,2rem+4vw,6rem)]">
      <header className="mx-auto max-w-grid">
        <p className="mb-3 text-body font-semibold text-ink-2">Developers</p>
        <h1 className="text-headline">PayLedger API</h1>
        <p className="mt-5 max-w-[42rem] text-lead text-pretty text-ink-2">
          A JSON API over HTTP. Amounts are integer paise (₹1 = 100). Errors follow RFC 7807. The full,
          generated reference lives in Swagger UI at <C>/swagger-ui.html</C>.
        </p>
      </header>

      <div className="mx-auto mt-12 grid max-w-grid grid-cols-1 gap-10 md:mt-16 lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-16">
        <Sidebar />

        <div className="min-w-0 space-y-16">
          <Doc id="quickstart" title="Quickstart">
            <p>Run PayLedger with Docker, register a merchant, then create your first payment.</p>
            <h3>1. Start everything</h3>
            <CodePanel title="Start PayLedger" samples={START} />
            <h3>2. Register a merchant</h3>
            <p>The response contains the API key. It is shown <strong className="text-ink">once</strong> and stored only as a SHA-256 hash, so save it now. A <C>dashboardPassword</C> (12 to 72 characters) also creates a dashboard login.</p>
            <CodePanel title="Register a merchant" samples={REGISTER} response={REGISTER_RESPONSE} />
            <h3>3. Create a payment</h3>
            <p>The bank call happens inside the request, so the answer is usually final: <C>SUCCEEDED</C> or <C>FAILED</C>.</p>
            <CodePanel title="Create a payment" samples={CREATE_PAYMENT} response={PAYMENT_RESPONSE} />
          </Doc>

          <Doc id="authentication" title="Authentication">
            <p>Two kinds of caller, both scoped to one merchant. Data of other merchants answers 404, never 403, so it leaks nothing.</p>
            <h3>Merchant servers: API key</h3>
            <p>Send <C>X-API-Key: plk_…</C> on every request. Keys are 256 random bits. v1 has no key rotation; register again if a key is lost.</p>
            <h3>Dashboard: JWT</h3>
            <p>Log in with email and password to get an HS256 JWT valid for one hour, then send <C>Authorization: Bearer &lt;token&gt;</C>. Wrong email and wrong password get the same answer.</p>
            <CodePanel title="Dashboard login" samples={LOGIN} response={LOGIN_RESPONSE} />
            <Table label="Permissions by caller" head={['Action', 'API key', 'Dashboard ADMIN', 'Dashboard VIEWER']} rows={[
              ['Create a payment', 'Yes', 'No (403)', 'No (403)'],
              ['Read payments and ledger', 'Yes', 'Yes', 'Yes'],
              ['Refund a payment', 'Yes', 'Yes', 'No (403)'],
            ]} />
          </Doc>

          <Doc id="payments" title="Payments">
            <Table label="Payment endpoints" head={['Endpoint', 'What it does']} rows={[
              [<C key="a">POST /api/v1/payments</C>, 'Create and process a payment. Needs an Idempotency-Key.'],
              [<C key="b">GET /api/v1/payments/{'{id}'}</C>, 'One payment with its status timeline and refunded total.'],
              [<C key="c">GET /api/v1/payments</C>, 'Newest first. Filters: status, from (inclusive), to (exclusive), page, size ≤ 100.'],
              [<C key="d">POST /api/v1/payments/{'{id}'}/refunds</C>, 'Full or partial refund of a SUCCEEDED payment. Needs an Idempotency-Key.'],
              [<C key="e">GET /api/v1/ledger/accounts/{'{id}'}/balance</C>, 'Cached balance and the balance recomputed from entries.'],
              [<C key="f">GET /api/v1/ledger/accounts/{'{id}'}/entries</C>, 'Ledger entries, newest first, paginated.'],
            ]} />
            <h3>Request fields</h3>
            <Table label="Create payment fields" head={['Field', 'Rules']} rows={[
              [<C key="a">amountPaise</C>, 'Required integer, 1 to 1,000,000,000 (₹1 crore).'],
              [<C key="b">currency</C>, 'Optional. Only INR in v1.'],
              [<C key="c">customerRef</C>, 'Required, up to 100 characters: letters, digits and . _ : @ -'],
            ]} />
            <h3>Statuses</h3>
            <p><C>CREATED</C> → <C>PROCESSING</C> → <C>SUCCEEDED</C> or <C>FAILED</C>; <C>FAILED</C> → <C>REVERSED</C>. A failed payment that already debited the customer carries <C>reverseBy</C>, the RBI T+1 deadline, and is reversed automatically. A timeout is never reported as FAILED: the payment stays PROCESSING until a status inquiry knows the answer.</p>
            <h3>Test outcomes</h3>
            <p>The mock bank picks outcomes at random by default. Start <C>customerRef</C> with one of these to force one:</p>
            <Table label="Simulator scenarios" head={['customerRef prefix', 'Result']} rows={[
              [<C key="1">sim_success</C>, 'SUCCEEDED'],
              [<C key="2">sim_decline</C>, 'FAILED, nothing debited'],
              [<C key="3">sim_debit_fail</C>, 'FAILED with reverseBy, then REVERSED'],
              [<C key="4">sim_timeout_success</C>, 'Bank times out, the status inquiry finds SUCCEEDED'],
              [<C key="5">sim_timeout_pending</C>, 'Stays PROCESSING until the inquiry job resolves it'],
              [<C key="6">sim_bank_down</C>, 'Bank unreachable with no record: FAILED, nothing to reverse'],
            ]} />
            <h3>Refunds</h3>
            <p>Refunds may be partial. Their total can never exceed the payment; concurrent refunds are serialised with a row lock.</p>
            <CodePanel title="Refund a payment" samples={REFUND} response={REFUND_RESPONSE} />
          </Doc>

          <Doc id="idempotency" title="Idempotency">
            <p><C>Idempotency-Key</C> is required on <C>POST /payments</C> and on refunds. Keys are up to 100 characters and belong to one merchant. “Same body” means the same amountPaise, currency and customerRef; spacing and key order in the JSON do not matter.</p>
            <Table label="Idempotency outcomes" head={['Situation', 'Answer']} rows={[
              ['First request with this key', '201, the payment is created and processed'],
              ['Same key, same body, after it finished', '201 with the stored result and Idempotent-Replayed: true'],
              ['Same key, different body', '422 Unprocessable Content'],
              ['Same key while the first is still running', '409 Conflict: retry shortly with the same key'],
              ['First request failed before a payment existed', 'Key released: retrying with the same key is safe'],
            ]} />
            <p>Under test, 50 identical requests sent at the same instant produced exactly one payment and one bank charge.</p>
          </Doc>

          <Doc id="webhooks" title="Webhooks">
            <p className="rounded-tile border border-dashed border-ink-2 px-5 py-4 text-ink">
              <strong>Planned for v2.</strong> v1 sends no webhooks.
            </p>
            <p>Today you can register an <C>https</C> URL as <C>webhookUrl</C> when creating the merchant; it is validated and stored. Signed event delivery with retries, built on a transactional outbox, is planned for v2.</p>
          </Doc>

          <Doc id="errors" title="Errors">
            <p>Every error is <C>application/problem+json</C> (RFC 7807) with <C>title</C>, <C>status</C> and <C>detail</C>. A 500 never contains internals; it carries an <C>errorId</C> that also appears in the server log.</p>
            <CodePanel title="Error example" samples={ERROR_422} />
            <CodePanel title="Validation error example" samples={ERROR_400} />
            <Table label="Status codes" head={['Status', 'Meaning']} rows={[
              ['400', 'Validation failed, malformed JSON, bad parameter or missing Idempotency-Key'],
              ['401', 'Missing or invalid API key or token; failed login'],
              ['403', 'Authenticated, but your role may not do this'],
              ['404', 'Not found, or it belongs to another merchant'],
              ['409', 'Request still in progress, illegal status change, refund of a non-SUCCEEDED payment, duplicate email'],
              ['422', 'Idempotency-Key reused with a different body; refund larger than what is left'],
              ['500', 'Unexpected error: details stay in the log, the response has an errorId'],
            ]} />
          </Doc>
        </div>
      </div>
    </div>
  );
}
