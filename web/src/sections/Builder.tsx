// Builder (black section): who made PayLedger, and the numbers the test suite actually measured.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Counter } from '../components/Counter';
import { PillButton } from '../components/PillButton';
import { Reveal } from '../components/Reveal';
import { Section } from '../components/Section';
import { BENCH, GITHUB_URL, LINKEDIN_URL } from '../content/site';
import { count } from '../lib/money';

const pct = (n: number) => `${n.toFixed(1)}%`;

const STATS = [
  { value: BENCH.backendTests + BENCH.dashboardTests, format: count, label: 'automated tests, all passing' },
  { value: BENCH.lineCoveragePct, format: pct, label: 'line coverage (JaCoCo)' },
  { value: BENCH.chaosPayments, format: count, label: 'payments in the chaos test' },
  { value: BENCH.paiseLost, format: count, label: 'paise lost, 0 double charges' },
];

export function Builder() {
  return (
    <Section id="builder" tone="night" labelledBy="builder-title">
      <div className="mx-auto max-w-copy text-center">
        <p className="mb-3 text-body font-semibold text-night-2">Builder</p>
        <h2 id="builder-title" className="text-title text-balance md:text-[2.5rem] md:leading-[1.1]">
          Designed and built by Giddaluru Damodhar (MrDamodar), Agentic AI Engineer.
        </h2>
        <p className="mx-auto mt-5 max-w-[40rem] text-lead text-pretty text-night-2">
          An engineering project, measured rather than claimed.
        </p>
      </div>

      <Reveal className="mx-auto mt-14 max-w-grid">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-10 md:grid-cols-4">
          {STATS.map((s) => (
            <div key={s.label} className="text-center">
              <dt className="sr-only">{s.label}</dt>
              <dd className="text-headline tabular-nums">
                <Counter value={s.value} format={s.format} />
              </dd>
              <dd className="mt-2 text-small text-night-2">{s.label}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-10 text-center text-micro text-night-2">{BENCH.source}</p>
      </Reveal>

      <div className="mt-12 flex flex-wrap items-center justify-center gap-x-10 gap-y-4">
        <PillButton href={GITHUB_URL} variant="link" external>GitHub</PillButton>
        <PillButton href={LINKEDIN_URL} variant="link" external>LinkedIn</PillButton>
      </div>
    </Section>
  );
}
