/**
 * One payment: amounts, bank details, T+1 deadline, the status timeline and the refund button.
 * While the outcome can still change (PROCESSING, or FAILED waiting for its reversal) the page
 * refreshes itself every few seconds.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { useCallback, useEffect, useState } from 'react';
import { getPayment } from '../api';
import { formatIst } from '../format';
import { formatPaise } from '../money';
import type { Payment } from '../types';
import RefundForm from '../components/RefundForm';
import StatusBadge from '../components/StatusBadge';
import Timeline from '../components/Timeline';

const POLL_MS = 3000;

function stillChanging(p: Payment): boolean {
  return p.status === 'CREATED' || p.status === 'PROCESSING' || (p.status === 'FAILED' && p.reverseBy !== null);
}

export default function PaymentDetailPage({ id, canRefund }: { id: string; canRefund: boolean }) {
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPayment(await getPayment(id));
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!payment || !stillChanging(payment)) return;
    const timer = window.setTimeout(() => void load(), POLL_MS);
    return () => window.clearTimeout(timer);
  }, [payment, load]);

  if (error && !payment) {
    return (
      <section>
        <a href="#/payments">← All payments</a>
        <p className="error" role="alert">{error}</p>
      </section>
    );
  }
  if (!payment) return <p className="muted">Loading…</p>;

  const refundable = payment.amountPaise - payment.refundedPaise;

  return (
    <section>
      <a href="#/payments">← All payments</a>
      <div className="page-head">
        <h2 className="amount">{formatPaise(payment.amountPaise)}</h2>
        <StatusBadge status={payment.status} />
      </div>
      {notice && <p className="notice" role="status">{notice}</p>}

      <div className="detail-grid">
        <div className="card">
          <h3>Details</h3>
          <dl>
            <dt>Payment ID</dt><dd className="mono">{payment.id}</dd>
            <dt>Customer</dt><dd>{payment.customerRef}</dd>
            <dt>Created</dt><dd>{formatIst(payment.createdAt)}</dd>
            <dt>Bank reference</dt><dd className="mono">{payment.bankReference ?? '—'}</dd>
            {payment.failureReason && (<><dt>Failure reason</dt><dd>{payment.failureReason}</dd></>)}
            {payment.reverseBy && (
              <>
                <dt>Reverse by (T+1)</dt>
                <dd>
                  {formatIst(payment.reverseBy)}
                  {payment.status === 'FAILED' && <span className="tag tag-warn">reversal pending</span>}
                  {payment.status === 'REVERSED' && <span className="tag tag-ok">money returned</span>}
                </dd>
              </>
            )}
            <dt>Refunded</dt><dd>{formatPaise(payment.refundedPaise)}</dd>
          </dl>

          {payment.status === 'SUCCEEDED' && (
            <div className="refund-area">
              {!canRefund && <p className="muted">Viewers cannot refund. Ask an admin.</p>}
              {canRefund && refundable > 0 && (
                <RefundForm paymentId={payment.id} refundablePaise={refundable}
                            onRefunded={(r) => {
                              setNotice(`Refunded ${formatPaise(r.amountPaise)}. ${formatPaise(r.refundablePaise)} left to refund.`);
                              void load();
                            }} />
              )}
              {canRefund && refundable === 0 && <p className="muted">Fully refunded.</p>}
            </div>
          )}
        </div>

        <div className="card">
          <h3>Timeline</h3>
          <Timeline entries={payment.timeline} />
          {stillChanging(payment) && <p className="muted small">Updating automatically…</p>}
        </div>
      </div>
    </section>
  );
}
