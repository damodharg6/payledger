// Footer: Apple-style. 12px grey text on #F5F5F7, four link columns, a hairline, copyright.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { AUTHOR, GITHUB_URL, LINKEDIN_URL } from '../content/site';

const COLUMNS = [
  {
    title: 'Product',
    links: [
      { label: 'Idempotency', href: '/#idempotency' },
      { label: 'Payment lifecycle', href: '/#lifecycle' },
      { label: 'Double-entry ledger', href: '/#ledger' },
      { label: 'Features', href: '/#features' },
    ],
  },
  {
    title: 'Developers',
    links: [
      { label: 'Quickstart', href: '/developers/#quickstart' },
      { label: 'Authentication', href: '/developers/#authentication' },
      { label: 'Payments API', href: '/developers/#payments' },
      { label: 'Errors', href: '/developers/#errors' },
    ],
  },
  {
    title: 'Project',
    links: [
      { label: 'Architecture', href: '/#architecture' },
      { label: 'Roadmap', href: '/#architecture' },
      { label: 'Builder', href: '/#builder' },
    ],
  },
  {
    title: 'Connect',
    links: [
      { label: 'GitHub', href: GITHUB_URL, external: true },
      { label: 'LinkedIn', href: LINKEDIN_URL, external: true },
    ],
  },
] as const;

export function Footer() {
  return (
    <footer className="bg-mist px-gutter pb-10 pt-12 text-micro text-ink-2">
      <div className="mx-auto max-w-grid">
        <p className="border-b border-hairline pb-4">
          PayLedger is an engineering portfolio project. It is not a licensed payment service and
          moves no real money. All figures on this site come from its own automated tests.
        </p>
        <nav aria-label="Footer" className="grid grid-cols-2 gap-x-6 gap-y-8 py-8 md:grid-cols-4">
          {COLUMNS.map((col) => (
            <div key={col.title}>
              <h2 className="mb-2.5 font-semibold text-ink">{col.title}</h2>
              <ul className="space-y-2">
                {col.links.map((l) => (
                  <li key={l.label}>
                    <a href={l.href}
                       {...('external' in l && l.external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
                       className="text-ink-2 hover:text-ink hover:underline underline-offset-2">
                      {l.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
        <div className="flex flex-col gap-2 border-t border-hairline pt-4 md:flex-row md:justify-between">
          <p>Copyright © 2026 Giddaluru Damodhar. All rights reserved.</p>
          <p>Designed and built by {AUTHOR}.</p>
        </div>
      </div>
    </footer>
  );
}
