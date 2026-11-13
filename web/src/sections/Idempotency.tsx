// Idempotency: the same request sent twice with the same Idempotency-Key. The first charges the
// customer; the retry gets the stored result back. The rows appear in order so the sequence
// (request, response, retry, replay) reads like a timeline.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { ArrowDown, ArrowRight } from '../lib/icons';
import { Counter } from '../components/Counter';
import { Headline } from '../components/Headline';
import { Reveal } from '../components/Reveal';
import { Section } from '../components/Section';
import { StatusPill } from '../components/StatusPill';
import { BENCH } from '../content/site';
import { count, inr } from '../lib/money';

const PAYMENT_ID = '7f3a9c2e-5b1d-4e8a-9c40-2d61f0b8a913';

function RequestCard({ label }: { label: string }) {
  return (
    <div className="rounded-tile bg-code p-5 text-left md:p-6">
      <p className="text-micro font-semibold text-code-dim">{label}</p>
      <pre className="mt-3 overflow-x-auto font-mono text-[13px] leading-6 text-code-text" tabIndex={0} aria-label={`${label}: HTTP request`}>
        <span className="tok-kw">POST</span> /api/v1/payments{'\n'}
        Idempotency-Key: <span className="tok-str">order-1001</span>{'\n'}
        {'{ '}<span className="tok-key">"amountPaise"</span>: <span className="tok-num">12500000</span>,{'\n'}
        {'  '}<span className="tok-key">"customerRef"</span>: <span className="tok-str">"cust-42"</span>{' }'}
      </pre>
    </div>
  );
}

function ResponseCard({ replay }: { replay: boolean }) {
  return (
    <div className="rounded-tile bg-white p-5 text-left shadow-soft ring-1 ring-black/5 md:p-6">
      <div className="flex items-center justify-between gap-3">
        <p className="font-mono text-small font-semibold text-ink">201 Created</p>
        <StatusPill status="SUCCEEDED" />
      </div>
      <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-small">
        <dt className="text-ink-2">Payment</dt>
        <dd className="truncate font-mono text-micro leading-5 text-ink">{PAYMENT_ID.slice(0, 18)}…</dd>
        <dt className="text-ink-2">Header</dt>
        <dd className="font-mono text-micro leading-5 text-ink">{replay ? 'Idempotent-Replayed: true' : '—'}</dd>
        <dt className="text-ink-2">Bank charges</dt>
        <dd className="font-semibold text-ink">
          1 {replay && <span className="font-normal text-ink-2">· unchanged, same payment</span>}
          {!replay && <span className="font-normal text-ink-2">· {inr(12_500_000)} debited</span>}
        </dd>
      </dl>
    </div>
  );
}

function Exchange({ label, replay, delay }: { label: string; replay: boolean; delay: number }) {
  return (
    <Reveal delay={delay} className="grid items-center gap-3 md:grid-cols-[1fr_auto_1fr] md:gap-6">
      <RequestCard label={label} />
      <ArrowRight aria-hidden className="mx-auto hidden size-6 text-ink-2 md:block" />
      <ArrowDown aria-hidden className="mx-auto size-5 text-ink-2 md:hidden" />
      <ResponseCard replay={replay} />
    </Reveal>
  );
}

export function Idempotency() {
  return (
    <Section id="idempotency" tone="mist" labelledBy="idempotency-title">
      <Headline
        id="idempotency-title"
        eyebrow="Idempotency"
        sub="Networks drop. Clients retry. Send the same request with the same Idempotency-Key and you get the stored result back, never a second charge."
      >
        <span className="whitespace-nowrap">Charged once.</span> <span className="whitespace-nowrap">Always.</span>
      </Headline>

      <div className="mx-auto mt-14 max-w-[60rem] space-y-6 md:mt-20 md:space-y-8">
        <Exchange label="Request 1" replay={false} delay={0} />
        <Exchange label="Request 2 · the client retries" replay delay={0.35} />
        <Reveal delay={0.6}>
          <ul className="flex flex-col items-center gap-1 font-mono text-micro text-ink-2 md:flex-row md:justify-center md:gap-8 md:text-small">
            <li>Same key, different body → <span className="text-ink">422</span></li>
            <li>First request still running → <span className="text-ink">409</span></li>
          </ul>
        </Reveal>
      </div>

      <Reveal className="mx-auto mt-16 max-w-copy text-center md:mt-24">
        <div className="mx-auto grid max-w-[36rem] grid-cols-[1fr_auto_1fr] items-center gap-x-4 gap-y-2 md:gap-x-10">
          <p className="text-headline tabular-nums">
            <Counter value={BENCH.concurrentRetries} format={count} />
          </p>
          <ArrowRight aria-hidden className="size-7 text-ink-2" />
          <p className="text-headline tabular-nums">{BENCH.chargesFromRetries}</p>
          <p className="self-start text-body text-balance text-ink-2">identical requests at once</p>
          <span aria-hidden />
          <p className="self-start text-body text-balance text-ink-2">charge at the bank</p>
        </div>
        <p className="mx-auto mt-6 max-w-[36rem] text-micro text-ink-2">
          50 threads, one key, one instant: one payment, one bank request, one ledger posting.
          From DoubleChargeProofIntegrationTest. {BENCH.source}
        </p>
      </Reveal>
    </Section>
  );
}
