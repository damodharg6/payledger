/**
 * A tiny hash router (#/payments, #/payments/<id>), so the dashboard needs no routing library
 * and works from any static file server without server-side rewrite rules.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { useEffect, useState } from 'react';

export type Route = { name: 'payments' } | { name: 'payment'; id: string };

export function parseRoute(hash: string): Route {
  const match = /^#\/payments\/([0-9a-fA-F-]{36})$/.exec(hash);
  return match ? { name: 'payment', id: match[1] } : { name: 'payments' };
}

export function navigate(route: Route): void {
  window.location.hash = route.name === 'payment' ? `#/payments/${route.id}` : '#/payments';
}

export function useRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
