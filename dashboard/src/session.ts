/**
 * The signed-in dashboard user. Kept in sessionStorage so a page refresh keeps you signed in but
 * closing the tab signs you out.
 *
 * Trade-off (interview note): any script running on the page can read sessionStorage, so XSS
 * could steal the token. An httpOnly cookie would hide it from scripts but then needs CSRF
 * protection. For v1 we keep the token short-lived (1 hour), render no user HTML, and accept
 * the trade-off.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import type { LoginResponse, Role } from './types';

const KEY = 'payledger.session';

export interface Session {
  token: string;
  merchantName: string;
  email: string;
  role: Role;
  expiresAt: number;
}

export function saveSession(login: LoginResponse, now: number = Date.now()): Session {
  const session: Session = {
    token: login.accessToken,
    merchantName: login.merchantName,
    email: login.email,
    role: login.role,
    expiresAt: now + login.expiresInSeconds * 1000,
  };
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // Storage can be blocked (private mode); the session then lives only in memory.
  }
  return session;
}

export function loadSession(now: number = Date.now()): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return session.expiresAt > now ? session : null;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}
