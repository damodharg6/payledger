// PaymentCard: a code-built rendering of one payment, its status and its timeline.
// Every step is always rendered (pending steps are just quieter), so the card never changes
// height while it animates: no layout shift.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Check, X } from '../lib/icons';
import type { ReactNode } from 'react';
import { inr } from '../lib/money';
import type { TimelineStep } from '../lib/types';
import { StatusPill } from './StatusPill';

interface Props {
  merchant: string;
  amountPaise: number;
  customer: string;
  steps: TimelineStep[];
  /** Index of the last step reached. */
  reached: number;
  footer?: ReactNode;
  className?: string;
}

function dotClass(status: TimelineStep['status'], done: boolean): string {
  if (!done) return 'border-hairline bg-white';
  if (status === 'SUCCEEDED') return 'border-success bg-success';
  if (status === 'FAILED') return 'border-danger bg-danger';
  return 'border-ink bg-ink';
}

export function PaymentCard({ merchant, amountPaise, customer, steps, reached, footer, className = '' }: Props) {
  const current = steps[Math.min(reached, steps.length - 1)];
  return (
    <article
      aria-label={`Payment of ${inr(amountPaise)} to ${merchant}, status ${current.status}`}
      className={`w-full rounded-card bg-white p-6 text-left shadow-card ring-1 ring-black/5 md:p-7 ${className}`}
    >
      <header className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-small text-ink-2">{merchant}</p>
          <p className="mt-1 text-[clamp(1.75rem,1.4rem+1.2vw,2.25rem)] font-semibold leading-tight tracking-[-0.02em] tabular-nums text-ink">
            {inr(amountPaise)}
          </p>
          <p className="mt-1 truncate text-small text-ink-2">UPI · {customer}</p>
        </div>
        <StatusPill status={current.status} />
      </header>

      <ol className="mt-6 space-y-3 border-t border-hairline/70 pt-5" aria-label="Timeline">
        {steps.map((step, i) => {
          const done = i <= reached;
          const Icon = step.status === 'FAILED' ? X : Check;
          return (
            <li key={step.status} className="flex items-center gap-3 text-small">
              <span aria-hidden className={`grid size-5 shrink-0 place-items-center rounded-full border transition-colors duration-500 ${dotClass(step.status, done)}`}>
                <Icon className={`size-3 text-white transition-opacity duration-300 ${done ? 'opacity-100' : 'opacity-0'}`} strokeWidth={3} />
              </span>
              <span className={`w-[6.75rem] shrink-0 font-medium transition-colors duration-500 ${done ? 'text-ink' : 'text-ink-2'}`}>
                {step.status}
                <span className="sr-only">{done ? ', done' : ', pending'}</span>
              </span>
              <span className="hidden min-w-0 flex-1 truncate text-ink-2 md:block">{step.reason}</span>
              {/* Fixed width keeps the row stable; text only exists once the step is reached
                  (no half-transparent text that would fail contrast mid-fade). */}
              <span className="ml-auto w-16 shrink-0 text-right font-mono text-micro tabular-nums text-ink-2">
                {done ? step.time : ''}
              </span>
            </li>
          );
        })}
      </ol>

      {footer && <div className="card-footer mt-5 border-t border-hairline/70 pt-4 text-small text-ink-2">{footer}</div>}
    </article>
  );
}
