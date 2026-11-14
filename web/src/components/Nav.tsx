// Nav: 48px sticky, translucent bar with backdrop blur. Wordmark left, links centre, one pill
// CTA right. Below 735px the links move into an accessible menu.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Menu, X } from '../lib/icons';
import { useEffect, useId, useState } from 'react';
import { DOCS_URL, NAV_LINKS } from '../content/site';
import { PillButton } from './PillButton';
import { withBase } from '../lib/paths';

export function Nav({ current }: { current?: string }) {
  const [open, setOpen] = useState(false);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  return (
    <header className="sticky top-0 z-50 h-nav border-b border-black/[0.08] bg-white/80 backdrop-blur-xl backdrop-saturate-150">
      <nav aria-label="Main" className="mx-auto flex h-full max-w-grid items-center justify-between gap-6 px-gutter">
        <a href={withBase('')} className="text-[17px] font-semibold tracking-[-0.01em] text-ink">
          PayLedger
        </a>

        <ul className="hidden items-center gap-7 md:flex">
          {NAV_LINKS.map((l) => (
            <li key={l.href}>
              <a href={l.href} aria-current={current === l.label ? 'page' : undefined}
                 className="text-micro text-ink/80 transition-colors hover:text-ink aria-[current=page]:text-ink aria-[current=page]:font-semibold">
                {l.label}
              </a>
            </li>
          ))}
        </ul>

        <div className="flex items-center gap-2">
          <PillButton href={DOCS_URL} size="sm">Read the docs</PillButton>
          <button
            type="button"
            className="grid size-11 place-items-center rounded-full text-ink md:hidden"
            aria-expanded={open}
            aria-controls={menuId}
            aria-label={open ? 'Close menu' : 'Open menu'}
            onClick={() => setOpen((v) => !v)}
          >
            {open ? <X aria-hidden className="size-5" /> : <Menu aria-hidden className="size-5" />}
          </button>
        </div>
      </nav>

      <div id={menuId} hidden={!open} className="border-b border-black/[0.08] bg-white/95 backdrop-blur-xl md:hidden">
        <ul className="mx-auto max-w-grid px-gutter py-3">
          {NAV_LINKS.map((l) => (
            <li key={l.href}>
              <a href={l.href} onClick={() => setOpen(false)} className="block py-3 text-title font-semibold text-ink">
                {l.label}
              </a>
            </li>
          ))}
        </ul>
      </div>
    </header>
  );
}
