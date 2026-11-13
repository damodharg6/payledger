// Features: a six-tile bento grid. Each tile has a small visual built in code, and a chip that
// says whether the feature is in v1 or planned (v2/v3), so the page never over-claims.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { ArrowDown, ArrowRight } from '../lib/icons';
import { m, useReducedMotion } from 'motion/react';
import { BentoTile } from '../components/BentoTile';
import { Headline } from '../components/Headline';
import { Section } from '../components/Section';
import { StatusPill } from '../components/StatusPill';
import { BENCH } from '../content/site';
import { inr } from '../lib/money';
import { EASE } from '../lib/motion';

/** The mock bank's default mix, straight from application.yml (payledger.bank.*). */
const BANK_MIX = [
  { label: 'Success', pct: 85, color: 'bg-success' },
  { label: 'Declined', pct: 7, color: 'bg-ink-2' },
  { label: 'Debited, failed', pct: 3, color: 'bg-danger' },
  { label: 'Timeout', pct: 5, color: 'bg-ink' },
];

function SimulatorVisual() {
  const reduce = useReducedMotion();
  return (
    <div className="w-full">
      <div className="flex h-3 w-full overflow-hidden rounded-full bg-mist" role="img"
           aria-label="Default bank mix: 85% success, 7% declined, 3% debited then failed, 5% timeout">
        {BANK_MIX.map((s, i) => (
          <m.span
            key={s.label}
            className={`${s.color} h-full origin-left`}
            style={{ width: `${s.pct}%` }}
            initial={{ scaleX: 0 }}
            whileInView={{ scaleX: 1 }}
            viewport={{ once: true }}
            transition={{ duration: reduce ? 0 : 0.6, ease: EASE, delay: reduce ? 0 : 0.2 + i * 0.25 }}
          />
        ))}
      </div>
      <ul className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2 text-small md:grid-cols-4">
        {BANK_MIX.map((s) => (
          <li key={s.label} className="flex items-center gap-2">
            <span aria-hidden className={`size-2 shrink-0 rounded-full ${s.color}`} />
            <span className="text-ink-2">{s.label}</span>
            <span className="ml-auto font-medium tabular-nums md:ml-0">{s.pct}%</span>
          </li>
        ))}
      </ul>
      <p className="mt-5 flex flex-wrap gap-2 font-mono text-micro">
        {['sim_success', 'sim_decline', 'sim_debit_fail', 'sim_timeout_pending', 'sim_bank_down'].map((c) => (
          <code key={c} className="rounded-md bg-mist px-2 py-1 text-ink">{c}</code>
        ))}
      </p>
    </div>
  );
}

function KeysVisual() {
  return (
    <div className="w-full rounded-control bg-mist p-4 font-mono text-micro leading-6">
      <p className="flex justify-between gap-3"><span className="text-ink">plk_3vV5Xk…Q9wE</span><span className="text-ink-2">shown once</span></p>
      <p className="my-1 flex items-center gap-2 text-ink-2"><ArrowDown aria-hidden className="size-3.5" /> SHA-256</p>
      <p className="flex justify-between gap-3"><span className="text-ink">9f2c4b7e…a1e41a</span><span className="text-ink-2">stored</span></p>
    </div>
  );
}

function WebhooksVisual() {
  return (
    <div className="w-full rounded-control border border-dashed border-hairline p-4 font-mono text-micro leading-6">
      <p className="text-ink">payment.succeeded</p>
      <p className="flex items-center gap-2 text-ink-2"><ArrowRight aria-hidden className="size-3.5 shrink-0" /> <span className="truncate">https://shop.example/hooks</span></p>
      <p className="text-ink-2">signed · retried with backoff</p>
    </div>
  );
}

function ResilienceVisual() {
  const steps = ['Charge', 'No answer in 2 s', 'Status inquiry'];
  return (
    <ol className="flex w-full flex-col gap-2 text-small md:flex-row md:items-center md:gap-3" aria-label="What happens on a bank timeout">
      {steps.map((s) => (
        <li key={s} className="flex items-center gap-2 md:gap-3">
          <span className="rounded-pill bg-mist px-3 py-1.5 text-ink">{s}</span>
          <ArrowRight aria-hidden className="hidden size-4 shrink-0 text-ink-2 md:block" />
        </li>
      ))}
      <li><StatusPill status="SUCCEEDED" /></li>
    </ol>
  );
}

function ReconciliationVisual() {
  return (
    <div className="w-full rounded-control bg-mist p-4">
      <p className="text-micro text-ink-2">Trial balance after the {BENCH.chaosPayments.toLocaleString('en-IN')}-payment test</p>
      <div className="mt-2 grid grid-cols-[1fr_auto_1fr] items-baseline gap-2 font-mono text-small tabular-nums">
        <span><span className="block text-micro text-ink-2">Debits</span>{inr(BENCH.chaosTrialBalancePaise)}</span>
        <span aria-label="equals" className="text-ink-2">=</span>
        <span className="text-right"><span className="block text-micro text-ink-2">Credits</span>{inr(BENCH.chaosTrialBalancePaise)}</span>
      </div>
    </div>
  );
}

function ObservabilityVisual() {
  const rows = [
    ['on_time', BENCH.chaosReversed],
    ['late', BENCH.chaosLateReversals],
    ['failed', 0],
  ] as const;
  return (
    <div className="w-full rounded-control bg-code p-4 font-mono text-micro leading-6 text-code-text">
      <p className="text-code-dim">payledger.reversals</p>
      {rows.map(([outcome, n]) => (
        <p key={outcome} className="flex justify-between gap-3 pl-3">
          <span>{'{'}outcome=<span className="tok-str">"{outcome}"</span>{'}'}</span>
          <span className="tabular-nums text-white">{n}</span>
        </p>
      ))}
    </div>
  );
}

export function Features() {
  return (
    <Section id="features" tone="mist" labelledBy="features-title">
      <Headline id="features-title" eyebrow="Features"
                sub="Most payment code handles success. These parts handle everything else.">
        <span className="whitespace-nowrap">Built for</span> <span className="whitespace-nowrap">the unhappy path.</span>
      </Headline>

      <div className="mx-auto mt-14 grid max-w-grid grid-cols-1 gap-5 md:mt-20 md:grid-cols-2 lg:grid-cols-6">
        <BentoTile className="md:col-span-2 lg:col-span-4" title="UPI bank simulator" status={{ label: 'In v1' }} visual={<SimulatorVisual />}>
          Configurable delays, declines, “debited but failed” and timeouts. Force any outcome in a test
          with a customer reference like sim_timeout_pending.
        </BentoTile>
        <BentoTile className="lg:col-span-2" delay={0.08} title="Hashed API keys" status={{ label: 'In v1' }} visual={<KeysVisual />}>
          256 random bits, shown once, stored only as a SHA-256 hash.
        </BentoTile>
        <BentoTile className="lg:col-span-2" title="Webhooks" status={{ label: 'Planned · v2', planned: true }} visual={<WebhooksVisual />}>
          Merchants register an https webhook URL today. Signed delivery with retries comes in v2.
        </BentoTile>
        <BentoTile className="md:col-span-2 lg:col-span-4" delay={0.08} title="Resilience"
                   status={{ label: 'Timeouts in v1 · circuit breakers v2' }} visual={<ResilienceVisual />}>
          A timeout is never treated as a failure. PayLedger asks the bank what really happened, and a
          job keeps asking until it knows. Resilience4j circuit breakers arrive in v2.
        </BentoTile>
        <BentoTile className="lg:col-span-3" title="Nightly reconciliation" status={{ label: 'Planned · v3', planned: true }} visual={<ReconciliationVisual />}>
          Every test run already proves debits equal credits across the whole ledger. A nightly Spring
          Batch job will match it against bank files.
        </BentoTile>
        <BentoTile className="lg:col-span-3" delay={0.08} title="Observability"
                   status={{ label: 'Metrics in v1 · traces v3' }} visual={<ObservabilityVisual />}>
          Health, metrics and reversal counters through Actuator and Micrometer, here from the
          1,000-payment test. OpenTelemetry traces are planned for v3.
        </BentoTile>
      </div>
    </Section>
  );
}
