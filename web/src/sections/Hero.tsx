// Hero: "Every rupee. Accounted for." with a payment card that plays its lifecycle once,
// CREATED → PROCESSING → SUCCEEDED. The headline renders without animation so it paints first.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { useReducedMotion } from 'motion/react';
import { useEffect, useState } from 'react';
import { PaymentCard } from '../components/PaymentCard';
import { PillButton } from '../components/PillButton';
import { DOCS_URL, GITHUB_URL } from '../content/site';
import type { TimelineStep } from '../lib/types';

const STEPS: TimelineStep[] = [
  { status: 'CREATED', reason: 'Payment created', time: '21:03:27' },
  { status: 'PROCESSING', reason: 'Sent to bank', time: '21:03:27' },
  { status: 'SUCCEEDED', reason: 'Bank confirmed payment', time: '21:03:28' },
];

function HeroPayment() {
  const reduce = useReducedMotion();
  const [reached, setReached] = useState(0);

  useEffect(() => {
    if (reduce) {
      setReached(2);
      return;
    }
    const timers = [window.setTimeout(() => setReached(1), 1100), window.setTimeout(() => setReached(2), 2400)];
    return () => timers.forEach(window.clearTimeout);
  }, [reduce]);

  return (
    <PaymentCard
      merchant="Chai Point"
      amountPaise={12_500_000}
      customer="cust-42"
      steps={STEPS}
      reached={reached}
      footer={
        <span className="flex items-center justify-between gap-3 font-mono text-micro">
          <span>Idempotency-Key</span>
          <span className="text-ink">order-1001</span>
        </span>
      }
    />
  );
}

export function Hero() {
  return (
    <section aria-labelledby="hero-title" className="bg-white px-gutter pb-section pt-[clamp(4rem,2rem+6vw,8.5rem)] text-center">
      <h1 id="hero-title" className="text-hero text-balance">
        Every rupee.
        <br />
        Accounted for.
      </h1>
      <p className="mx-auto mt-6 max-w-[38rem] text-lead text-pretty text-ink-2">
        No customer is charged twice. No rupee is lost. Failed payments are reversed within T+1.
      </p>
      <div className="mt-9 flex flex-wrap items-center justify-center gap-x-8 gap-y-5">
        <PillButton href={DOCS_URL}>Read the docs</PillButton>
        <PillButton href={GITHUB_URL} variant="link" external>View on GitHub</PillButton>
      </div>
      <div className="mx-auto mt-16 max-w-[28rem] md:mt-20">
        <HeroPayment />
        <p className="mt-5 text-micro text-ink-2">
          Status names and timeline text are exactly what the PayLedger API returns.
        </p>
      </div>
    </section>
  );
}
