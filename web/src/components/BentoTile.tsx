// BentoTile: one feature in the bento grid: status chip, title, one or two sentences, and a small
// code-built visual. The chip says honestly whether the feature ships in v1 or is planned.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import type { ReactNode } from 'react';
import { Reveal } from './Reveal';

export type TileStatus = { label: string; planned?: boolean };

interface Props {
  title: string;
  status: TileStatus;
  visual: ReactNode;
  className?: string;
  delay?: number;
  children: ReactNode;
}

export function BentoTile({ title, status, visual, className = '', delay = 0, children }: Props) {
  return (
    <Reveal delay={delay} className={`h-full ${className}`}>
      <article className="flex h-full flex-col rounded-tile bg-white p-6 ring-1 ring-black/5 md:p-8">
        <p className={`self-start rounded-pill px-2.5 py-1 text-micro font-semibold ${
          status.planned ? 'text-ink-2 ring-1 ring-hairline' : 'bg-mist text-ink'
        }`}>
          {status.label}
        </p>
        <h3 className="mt-4 text-title">{title}</h3>
        <p className="mt-2 max-w-[34rem] text-body text-pretty text-ink-2">{children}</p>
        <div className="mt-6 flex flex-1 items-end">{visual}</div>
      </article>
    </Reveal>
  );
}
