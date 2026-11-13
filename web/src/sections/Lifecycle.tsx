// Lifecycle: pinned scroll story. As the reader scrolls, one payment goes CREATED → PROCESSING →
// SUCCEEDED, then a second goes FAILED → REVERSED. One sentence per step.
// Scroll position only picks the step; the change itself is a short CSS opacity/transform
// transition (compositor-only, 60fps). Under prefers-reduced-motion nothing is pinned and every
// step is shown at once with both payments in their final state.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { useMotionValueEvent, useReducedMotion, useScroll } from 'motion/react';
import { useEffect, useRef, useState } from 'react';
import { PaymentCard } from '../components/PaymentCard';
import { inr } from '../lib/money';
import type { Status, TimelineStep } from '../lib/types';

const STORY: { status: Status; copy: string }[] = [
  { status: 'CREATED', copy: 'A payment is saved as CREATED before any money moves.' },
  { status: 'PROCESSING', copy: 'PROCESSING: the bank is asked, and no database lock is held while it thinks.' },
  { status: 'SUCCEEDED', copy: 'SUCCEEDED: the customer is debited and the merchant credited in one ledger transaction.' },
  { status: 'FAILED', copy: 'A second payment fails after the bank took the money, so it waits in clearing with a T+1 deadline.' },
  { status: 'REVERSED', copy: 'REVERSED: the auto-reversal job returns it to the customer, long before the deadline.' },
];

const PAYMENT_ONE: TimelineStep[] = [
  { status: 'CREATED', reason: 'Payment created', time: '21:03:27' },
  { status: 'PROCESSING', reason: 'Sent to bank', time: '21:03:27' },
  { status: 'SUCCEEDED', reason: 'Bank confirmed payment', time: '21:03:28' },
];

const PAYMENT_TWO: TimelineStep[] = [
  { status: 'CREATED', reason: 'Payment created', time: '21:03:28' },
  { status: 'PROCESSING', reason: 'Sent to bank', time: '21:03:28' },
  { status: 'FAILED', reason: 'Customer debited but credit to merchant failed', time: '21:03:28' },
  { status: 'REVERSED', reason: 'Customer debit reversed', time: '21:03:34' },
];

export function Lifecycle() {
  const ref = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();
  const [step, setStep] = useState(0);
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start start', 'end end'] });

  useMotionValueEvent(scrollYProgress, 'change', (v) => {
    if (!reduce) setStep(Math.min(STORY.length - 1, Math.max(0, Math.floor(v * STORY.length))));
  });
  useEffect(() => {
    if (reduce) setStep(STORY.length - 1);
  }, [reduce]);

  const second = step >= 3;
  const fade = 'transition-[opacity,translate] duration-500 ease-apple motion-reduce:opacity-100 motion-reduce:translate-y-0';

  return (
    <section id="lifecycle" aria-labelledby="lifecycle-title" className="bg-white px-gutter">
      <div ref={ref} className="relative h-[500vh] motion-reduce:h-auto motion-reduce:py-section">
        <div className="sticky top-nav flex h-[calc(100svh-var(--spacing-nav))] items-center motion-reduce:static motion-reduce:h-auto">
          <div className="mx-auto grid w-full max-w-grid items-center gap-8 md:gap-12 lg:grid-cols-[1fr_minmax(0,28rem)] lg:gap-20">
            <div>
              <p className="mb-3 text-body font-semibold text-ink-2 [@media(max-height:720px)]:hidden">Lifecycle</p>
              <h2 id="lifecycle-title" className="text-headline text-balance">
                <span className="whitespace-nowrap">Five states.</span> <span className="whitespace-nowrap">No surprises.</span>
              </h2>

              <ol className="mt-6 space-y-0.5 md:mt-8" aria-label="Payment lifecycle, step by step">
                {STORY.map((s, i) => {
                  const active = i === step;
                  return (
                    <li
                      key={s.status}
                      aria-current={active ? 'step' : undefined}
                      className={`border-l-2 py-2 pl-4 transition-colors duration-500 md:pl-5 ${
                        active ? 'border-ink text-ink' : 'hidden border-hairline text-ink-2 md:block motion-reduce:block'
                      }`}
                    >
                      <span className="block text-micro font-semibold tracking-[0.06em]">
                        {i < 3 ? 'PAYMENT 1' : 'PAYMENT 2'} · {s.status}
                      </span>
                      <span className="mt-0.5 block text-lead text-pretty md:text-body">{s.copy}</span>
                    </li>
                  );
                })}
              </ol>

              <div className="mt-4 flex gap-1.5 md:hidden motion-reduce:hidden" aria-hidden>
                {STORY.map((s, i) => (
                  <span key={s.status} className={`h-1 flex-1 rounded-full transition-colors duration-500 ${i <= step ? 'bg-ink' : 'bg-hairline'}`} />
                ))}
              </div>
            </div>

            {/* Both cards share one grid cell, so swapping them never changes the layout. */}
            <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] motion-reduce:gap-6 [@media(max-height:720px)]:[&_.card-footer]:hidden">
              <div
                aria-hidden={second}
                className={`col-start-1 row-start-1 ${fade} ${second ? 'pointer-events-none -translate-y-3 opacity-0' : 'translate-y-0 opacity-100'}`}
              >
                <PaymentCard
                  merchant="Chai Point · payment 1"
                  amountPaise={12_500_000}
                  customer="cust-42"
                  steps={PAYMENT_ONE}
                  reached={Math.min(step, 2)}
                  footer={step >= 2
                    ? <>Ledger: debit customer, credit merchant, {inr(12_500_000)} each</>
                    : <>Ledger: nothing posted while the outcome is unknown</>}
                />
              </div>
              <div
                aria-hidden={!second}
                className={`col-start-1 row-start-1 motion-reduce:row-start-2 ${fade} ${second ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-3 opacity-0'}`}
              >
                <PaymentCard
                  merchant="Chai Point · payment 2"
                  amountPaise={140_000}
                  customer="cust-77"
                  steps={PAYMENT_TWO}
                  reached={step >= 4 ? 3 : 2}
                  footer={step >= 4
                    ? <>Returned to the customer. Bank clearing is back to {inr(0)}.</>
                    : <>Held in bank clearing · reverse by 15 Nov 2026, 00:00 IST</>}
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
