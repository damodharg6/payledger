// Build-time renderer: turns each page into static HTML (see scripts/prerender.mjs).
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { renderToString } from 'react-dom/server';
import { DevelopersApp, HomeApp } from './apps';

export function render(page: 'home' | 'developers'): string {
  return renderToString(page === 'home' ? <HomeApp /> : <DevelopersApp />);
}
