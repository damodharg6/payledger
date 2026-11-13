/**
 * Payments list: status and date filters, newest first, 20 per page.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { useCallback, useEffect, useState } from 'react';
import { listPayments } from '../api';
import { formatIst, istDayEnd, istDayStart, shortId } from '../format';
import { formatPaise } from '../money';
import { navigate } from '../router';
import { PAYMENT_STATUSES, type Page, type PaymentStatus, type PaymentSummary } from '../types';
import StatusBadge from '../components/StatusBadge';

const PAGE_SIZE = 20;

export default function PaymentsPage() {
  const [status, setStatus] = useState<PaymentStatus | ''>('');
  const [fromDay, setFromDay] = useState('');
  const [toDay, setToDay] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<PaymentSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await listPayments({
        status: status || undefined,
        from: fromDay ? istDayStart(fromDay) : undefined,
        to: toDay ? istDayEnd(toDay) : undefined,
        page,
        size: PAGE_SIZE,
      }));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [status, fromDay, toDay, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const resetPage = <T,>(set: (v: T) => void) => (v: T) => {
    set(v);
    setPage(0);
  };

  return (
    <section>
      <div className="page-head">
        <h2>Payments</h2>
        <button className="btn btn-ghost" onClick={() => void load()} disabled={loading}>Refresh</button>
      </div>

      <div className="filters card">
        <label>
          Status
          <select value={status} onChange={(e) => resetPage(setStatus)(e.target.value as PaymentStatus | '')}>
            <option value="">All</option>
            {PAYMENT_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <label>
          From (IST)
          <input type="date" value={fromDay} onChange={(e) => resetPage(setFromDay)(e.target.value)} />
        </label>
        <label>
          To (IST)
          <input type="date" value={toDay} onChange={(e) => resetPage(setToDay)(e.target.value)} />
        </label>
        {(status || fromDay || toDay) && (
          <button className="btn btn-ghost" onClick={() => { setStatus(''); setFromDay(''); setToDay(''); setPage(0); }}>
            Clear
          </button>
        )}
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="card table-card">
        <table>
          <thead>
            <tr>
              <th>Created</th>
              <th className="hide-sm">Payment</th>
              <th>Customer</th>
              <th className="num">Amount</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((p) => (
              <tr key={p.id} className="clickable" tabIndex={0}
                  onClick={() => navigate({ name: 'payment', id: p.id })}
                  onKeyDown={(e) => e.key === 'Enter' && navigate({ name: 'payment', id: p.id })}>
                <td className="muted when">{formatIst(p.createdAt)}</td>
                <td className="mono hide-sm">{shortId(p.id)}</td>
                <td className="customer">{p.customerRef}</td>
                <td className="num">{formatPaise(p.amountPaise)}</td>
                <td className="status"><StatusBadge status={p.status} /></td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  No payments match. Payments are created by your server with its API key
                  (POST /api/v1/payments, see Swagger UI) or by <code>scripts/seed-demo.sh</code>.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>← Previous</button>
          <span className="muted">Page {data.page + 1} of {data.totalPages} · {data.totalElements} payments</span>
          <button className="btn btn-ghost" disabled={page + 1 >= data.totalPages || loading} onClick={() => setPage(page + 1)}>Next →</button>
        </div>
      )}
      {data && data.totalPages <= 1 && <p className="muted pager">{data.totalElements} payments</p>}
    </section>
  );
}
