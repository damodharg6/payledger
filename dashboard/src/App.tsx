/**
 * App shell: shows the login page until there is a session, then the header and the current
 * page (payments list or one payment).
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { useCallback, useEffect, useState } from 'react';
import { setToken, setUnauthorizedHandler } from './api';
import { useRoute } from './router';
import { clearSession, loadSession, saveSession, type Session } from './session';
import type { LoginResponse } from './types';
import LoginPage from './pages/LoginPage';
import PaymentsPage from './pages/PaymentsPage';
import PaymentDetailPage from './pages/PaymentDetailPage';

export default function App() {
  const [session, setSession] = useState<Session | null>(() => {
    const s = loadSession();
    setToken(s?.token ?? null);
    return s;
  });
  const route = useRoute();

  const logout = useCallback(() => {
    clearSession();
    setToken(null);
    setSession(null);
  }, []);

  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  // Sign out when the token expires, even if the user does nothing.
  useEffect(() => {
    if (!session) return;
    const timer = window.setTimeout(logout, Math.max(0, session.expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [session, logout]);

  const onLogin = (login: LoginResponse) => {
    const s = saveSession(login);
    setToken(s.token);
    setSession(s);
  };

  if (!session) {
    return <LoginPage onLogin={onLogin} />;
  }

  return (
    <div className="app">
      <header className="topbar">
        <a className="brand" href="#/payments">
          <span className="brand-mark" aria-hidden>₹</span> PayLedger
        </a>
        <div className="who">
          <span className="merchant">{session.merchantName}</span>
          <span className="muted">{session.email} · {session.role}</span>
          <button className="btn btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </header>
      <main className="content">
        {route.name === 'payment' ? (
          <PaymentDetailPage id={route.id} canRefund={session.role === 'ADMIN'} />
        ) : (
          <PaymentsPage />
        )}
      </main>
    </div>
  );
}
