/**
 * Refund button + form for a SUCCEEDED payment.
 *
 * One Idempotency-Key is kept per refund attempt: if the network drops and the user clicks
 * again, the same key is sent and the server refunds only once. The key changes after a
 * success, or when the amount or reason is edited (a new request needs a new key; reusing it
 * with a different body would be a 422).
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { useRef, useState, type FormEvent } from 'react';
import { ApiError, refundPayment } from '../api';
import { formatPaise, paiseToInput, parseRupeesToPaise } from '../money';
import type { Refund } from '../types';

interface Props {
  paymentId: string;
  refundablePaise: number;
  onRefunded: (refund: Refund) => void;
}

export default function RefundForm({ paymentId, refundablePaise, onRefunded }: Props) {
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState(paiseToInput(refundablePaise));
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const key = useRef<string>(crypto.randomUUID());

  const newKey = () => {
    key.current = crypto.randomUUID();
  };

  const amountPaise = parseRupeesToPaise(amount);
  const invalid = amountPaise === null || amountPaise > refundablePaise;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (amountPaise === null || invalid) return;
    setBusy(true);
    setError(null);
    try {
      const refund = await refundPayment(paymentId, amountPaise, reason, key.current);
      newKey();
      setOpen(false);
      setReason('');
      setAmount(paiseToInput(refund.refundablePaise));
      onRefunded(refund);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Network error: try again (it is safe, you will not be refunded twice)');
    } finally {
      setBusy(false);
    }
  };

  if (!open) {
    return (
      <button className="btn btn-primary" onClick={() => setOpen(true)}>
        Refund…
      </button>
    );
  }

  return (
    <form className="refund-form" onSubmit={submit}>
      <label>
        Amount (₹) <span className="muted">max {formatPaise(refundablePaise)}</span>
        <input inputMode="decimal" value={amount} autoFocus
               onChange={(e) => { setAmount(e.target.value); newKey(); }} aria-invalid={invalid} />
      </label>
      <label>
        Reason <span className="muted">optional</span>
        <input value={reason} maxLength={255} onChange={(e) => { setReason(e.target.value); newKey(); }} />
      </label>
      {invalid && <p className="error">Enter an amount between ₹0.01 and {formatPaise(refundablePaise)} (max 2 decimals)</p>}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="row">
        <button className="btn btn-danger" type="submit" disabled={busy || invalid}>
          {busy ? 'Refunding…' : `Refund ${amountPaise ? formatPaise(amountPaise) : ''}`}
        </button>
        <button className="btn btn-ghost" type="button" onClick={() => setOpen(false)} disabled={busy}>Cancel</button>
      </div>
    </form>
  );
}
