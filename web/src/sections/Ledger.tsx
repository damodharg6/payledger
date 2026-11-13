// Ledger (black section): "Debits equal credits. Every time." A double-entry table writes itself
// in, the totals balance, and the append-only rule is shown with the database's real error.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Headline } from '../components/Headline';
import { LedgerTable, type LedgerRow } from '../components/LedgerTable';
import { Reveal } from '../components/Reveal';
import { Section } from '../components/Section';

const ROWS: LedgerRow[] = [
  { txn: 'PAYMENT', note: 'Payment 1 succeeded', account: 'Customer · cust-42', debit: 12_500_000 },
  { txn: 'PAYMENT', account: 'Merchant · Chai Point', credit: 12_500_000 },
  { txn: 'PAYMENT', note: 'Payment 2 debited, then failed', account: 'Customer · cust-77', debit: 140_000 },
  { txn: 'PAYMENT', account: 'Bank clearing', credit: 140_000 },
  { txn: 'REVERSAL', note: 'T+1 auto-reversal', account: 'Bank clearing', debit: 140_000 },
  { txn: 'REVERSAL', account: 'Customer · cust-77', credit: 140_000 },
  { txn: 'REFUND', note: 'Partial refund', account: 'Merchant · Chai Point', debit: 100_050 },
  { txn: 'REFUND', account: 'Customer · cust-42', credit: 100_050 },
];

export function Ledger() {
  return (
    <Section id="ledger" tone="night" labelledBy="ledger-title">
      <Headline id="ledger-title" tone="night" eyebrow="Double-entry ledger"
                sub="Append-only. Mistakes are reversed, never erased.">
        <span className="whitespace-nowrap">Debits equal credits.</span> <span className="whitespace-nowrap">Every time.</span>
      </Headline>

      <Reveal className="mx-auto mt-14 max-w-[56rem] md:mt-20">
        <LedgerTable rows={ROWS} caption="Ledger entries for four transactions: a payment, a failed debit, its reversal and a refund" />
      </Reveal>

      <Reveal className="mx-auto mt-10 max-w-[56rem]">
        <div className="rounded-tile p-5 font-mono text-[12px] leading-6 ring-1 ring-night-line md:p-6 md:text-[13px]">
          <p className="text-night-2">
            <span aria-hidden>payledger=# </span>UPDATE ledger_entries SET amount_paise = 0 WHERE id = 7;
          </p>
          <p className="text-white">ERROR:  ledger_entries is append-only</p>
        </div>
        <p className="mt-4 text-center text-small text-night-2 text-pretty">
          A database trigger rejects every UPDATE and DELETE. The only way to change history is to
          add a new, balancing transaction.
        </p>
      </Reveal>
    </Section>
  );
}
