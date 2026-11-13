// Architecture: a minimal diagram. Thin lines, text labels, no icons. Solid = running in v1,
// dashed = planned for v2/v3, so the picture never claims more than the code does.
// Desktop gets an SVG; phones get the same flow as a vertical list (readable at 375px).
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Headline } from '../components/Headline';
import { Reveal } from '../components/Reveal';
import { Section } from '../components/Section';

type Box = { x: number; y: number; w: number; label: string; sub: string; planned?: boolean };
const H = 64;

const BOXES: Box[] = [
  { x: 10, y: 178, w: 150, label: 'Client', sub: 'merchant · dashboard' },
  { x: 195, y: 178, w: 150, label: 'API Gateway', sub: 'planned · v2', planned: true },
  { x: 395, y: 178, w: 170, label: 'Payments', sub: 'idempotency · states' },
  { x: 590, y: 178, w: 170, label: 'Ledger', sub: 'double-entry' },
  { x: 820, y: 178, w: 170, label: 'PostgreSQL 16', sub: 'Flyway migrations' },
  { x: 405, y: 30, w: 150, label: 'Redis', sub: 'planned · v2', planned: true },
  { x: 395, y: 326, w: 170, label: 'Bank / UPI simulator', sub: 'mock · in-process' },
  { x: 590, y: 326, w: 170, label: 'Kafka + Debezium', sub: 'outbox · planned v2', planned: true },
];

const LINES: { d: string; planned?: boolean }[] = [
  { d: 'M160 210 H195', planned: true },
  { d: 'M345 210 H395', planned: true },
  { d: 'M565 210 H590' },
  { d: 'M760 210 H820' },
  { d: 'M480 178 V94', planned: true },
  { d: 'M480 242 V326' },
  { d: 'M675 242 V326', planned: true },
  { d: 'M110 242 C 190 330, 320 310, 395 234' },
];

function Diagram() {
  return (
    <svg viewBox="0 0 1000 400" className="hidden h-auto w-full md:block" role="img" aria-labelledby="arch-svg-title arch-svg-desc">
      <title id="arch-svg-title">PayLedger architecture</title>
      <desc id="arch-svg-desc">
        Clients call the PayLedger Spring Boot application, which holds the Payments and Ledger modules
        and stores everything in PostgreSQL 16. Payments calls an in-process bank and UPI simulator.
        Planned for v2: an API gateway in front, Redis beside Payments, and Kafka with Debezium for
        outbox events.
      </desc>
      <rect x="375" y="148" width="405" height="124" rx="20" fill="none" stroke="var(--color-hairline)" />
      <text x="770" y="166" textAnchor="end" fontSize="11" fill="var(--color-ink-2)">PayLedger · one Spring Boot service</text>
      {LINES.map((l) => (
        <path key={l.d} d={l.d} fill="none" stroke={l.planned ? 'var(--color-ink-2)' : 'var(--color-ink)'}
              strokeWidth="1.25" strokeDasharray={l.planned ? '5 5' : undefined} />
      ))}
      {BOXES.map((b) => (
        <g key={b.label}>
          <rect x={b.x} y={b.y} width={b.w} height={H} rx="14" fill="var(--color-white)"
                stroke={b.planned ? 'var(--color-ink-2)' : 'var(--color-ink)'} strokeWidth="1.25"
                strokeDasharray={b.planned ? '5 5' : undefined} />
          <text x={b.x + b.w / 2} y={b.y + 28} textAnchor="middle" fontSize="15" fontWeight="600" fill="var(--color-ink)">{b.label}</text>
          <text x={b.x + b.w / 2} y={b.y + 47} textAnchor="middle" fontSize="11" fill="var(--color-ink-2)">{b.sub}</text>
        </g>
      ))}
      <text x="60" y="338" fontSize="11" fill="var(--color-ink-2)">v1: clients call the service directly</text>
    </svg>
  );
}

const FLOW: { label: string; sub: string; planned?: boolean }[] = [
  { label: 'Client', sub: 'merchant server · dashboard' },
  { label: 'API Gateway', sub: 'Planned · v2 (today clients call the service directly)', planned: true },
  { label: 'Payments', sub: 'idempotency · state machine' },
  { label: 'Ledger', sub: 'double-entry · append-only' },
  { label: 'PostgreSQL 16', sub: 'Flyway migrations' },
];
const AROUND: { label: string; sub: string; planned?: boolean }[] = [
  { label: 'Bank / UPI simulator', sub: 'mock, in-process in v1' },
  { label: 'Redis', sub: 'Planned · v2', planned: true },
  { label: 'Kafka + Debezium', sub: 'Planned · v2', planned: true },
];

function Node({ label, sub, planned }: { label: string; sub: string; planned?: boolean }) {
  return (
    <div className={`rounded-control bg-white px-4 py-3 text-center ${planned ? 'border border-dashed border-ink-2' : 'border border-ink'}`}>
      <p className="text-body font-semibold">{label}</p>
      <p className="text-micro text-ink-2">{sub}</p>
    </div>
  );
}

function MobileFlow() {
  return (
    <div className="md:hidden">
      <ol className="flex flex-col items-stretch" aria-label="Request path">
        {FLOW.map((n, i) => (
          <li key={n.label} className="flex flex-col items-center">
            {i > 0 && <span aria-hidden className={`h-6 w-px ${n.planned || FLOW[i - 1].planned ? 'border-l border-dashed border-ink-2' : 'bg-ink'}`} />}
            <div className="w-full"><Node {...n} /></div>
          </li>
        ))}
      </ol>
      <p className="mb-3 mt-8 text-center text-small font-semibold text-ink-2">Around it</p>
      <ul className="grid grid-cols-1 gap-3">
        {AROUND.map((n) => <li key={n.label}><Node {...n} /></li>)}
      </ul>
    </div>
  );
}

export function Architecture() {
  return (
    <Section id="architecture" tone="mist" labelledBy="architecture-title">
      <Headline id="architecture-title" eyebrow="Architecture"
                sub="v1 is one Spring Boot service on PostgreSQL. Solid lines run today; dashed ones are the v2 and v3 plan.">
        Simple by design.
      </Headline>
      <Reveal className="mx-auto mt-14 max-w-grid md:mt-20">
        <Diagram />
        <MobileFlow />
      </Reveal>
      <Reveal className="mx-auto mt-12 max-w-copy">
        <dl className="grid gap-x-10 gap-y-4 text-small md:grid-cols-3">
          <div><dt className="font-semibold">v1 · now</dt><dd className="text-ink-2">Java 21, Spring Boot 4.1, PostgreSQL 16, Flyway, React dashboard, Docker, CI.</dd></div>
          <div><dt className="font-semibold">v2 · planned</dt><dd className="text-ink-2">Kafka, transactional outbox, saga, signed webhooks, Resilience4j, rate limiting.</dd></div>
          <div><dt className="font-semibold">v3 · planned</dt><dd className="text-ink-2">Kubernetes, OpenTelemetry, Grafana, k6 load tests, reconciliation.</dd></div>
        </dl>
      </Reveal>
    </Section>
  );
}
