// LedgerTable: double-entry rows that write in one by one, then debit and credit totals that
// count up to the same number. Built for the black section. A real <table> with header cells,
// so screen readers get the structure, not just the look.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Check } from '../lib/icons';
import { m, useReducedMotion } from 'motion/react';
import { Counter } from './Counter';
import { inr } from '../lib/money';
import { DURATION, EASE } from '../lib/motion';

export interface LedgerRow {
  txn: 'PAYMENT' | 'REVERSAL' | 'REFUND';
  /** Shown on the first line of each transaction. */
  note?: string;
  account: string;
  debit?: number;
  credit?: number;
}

const STAGGER = 0.12;

export function LedgerTable({ rows, caption }: { rows: LedgerRow[]; caption: string }) {
  const reduce = useReducedMotion();
  const debits = rows.reduce((s, r) => s + (r.debit ?? 0), 0);
  const credits = rows.reduce((s, r) => s + (r.credit ?? 0), 0);
  const totalsDelay = reduce ? 0 : rows.length * STAGGER + 0.2;

  return (
    <div className="rounded-card bg-[#0d0d0e] p-4 ring-1 ring-night-line md:p-8">
      <table className="w-full border-collapse text-left text-[13px] md:text-small">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-night-line text-micro uppercase tracking-[0.06em] text-night-2">
            <th scope="col" className="hidden py-3 pr-4 font-semibold md:table-cell">Transaction</th>
            <th scope="col" className="py-3 pr-3 font-semibold">Account</th>
            <th scope="col" className="py-3 pr-1 text-right font-semibold">Debit</th>
            <th scope="col" className="py-3 pl-3 text-right font-semibold">Credit</th>
          </tr>
        </thead>
        <m.tbody
          initial="hidden"
          whileInView="shown"
          viewport={{ once: true, margin: '0px 0px -15% 0px' }}
          variants={{ shown: { transition: { staggerChildren: reduce ? 0 : STAGGER } } }}
        >
          {rows.map((r, i) => {
            const firstOfTxn = i === 0 || rows[i - 1].txn !== r.txn || r.note !== undefined;
            return (
              <m.tr
                key={`${r.txn}-${r.account}-${i}`}
                variants={{
                  hidden: { opacity: 0, y: reduce ? 0 : 8 },
                  shown: { opacity: 1, y: 0, transition: { duration: reduce ? 0 : 0.45, ease: EASE } },
                }}
                className={firstOfTxn && i > 0 ? 'border-t border-night-line' : ''}
              >
                <td className="hidden py-2.5 pr-4 align-top md:table-cell">
                  {firstOfTxn && (
                    <>
                      <span className="font-mono text-micro font-semibold text-white">{r.txn}</span>
                      {r.note && <span className="block text-micro text-night-2">{r.note}</span>}
                    </>
                  )}
                </td>
                <td className="py-2.5 pr-3 align-top text-white">
                  {firstOfTxn && <span className="block font-mono text-[11px] font-semibold text-night-2 md:hidden">{r.txn}</span>}
                  {r.account}
                </td>
                <td className="py-2.5 pr-1 text-right align-top font-mono tabular-nums text-white">{r.debit ? inr(r.debit) : ''}</td>
                <td className="py-2.5 pl-3 text-right align-top font-mono tabular-nums text-white">{r.credit ? inr(r.credit) : ''}</td>
              </m.tr>
            );
          })}
        </m.tbody>
        <tfoot>
          <tr className="border-t-2 border-white/40 font-semibold text-white">
            <th scope="row" className="hidden py-3 pr-4 md:table-cell">Total</th>
            <td className="py-3 pr-3"><span className="md:hidden">Total</span></td>
            <td className="py-3 pr-1 text-right font-mono">
              <Counter value={debits} format={inr} delay={totalsDelay} duration={1.1} />
            </td>
            <td className="py-3 pl-3 text-right font-mono">
              <Counter value={credits} format={inr} delay={totalsDelay} duration={1.1} />
            </td>
          </tr>
        </tfoot>
      </table>

      <m.p
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: reduce ? 0 : DURATION, ease: EASE, delay: reduce ? 0 : totalsDelay + 1.1 }}
        className="mt-4 flex items-center justify-end gap-2 text-small font-semibold text-success"
      >
        <Check aria-hidden className="size-4" strokeWidth={3} />
        Balanced: debits equal credits
      </m.p>
    </div>
  );
}
