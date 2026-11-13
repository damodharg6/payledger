// Headline: centred section heading with optional eyebrow and subhead.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import type { ReactNode } from 'react';
import type { Tone } from './Section';

interface Props {
  id: string;
  as?: 'h1' | 'h2';
  size?: 'hero' | 'headline';
  eyebrow?: string;
  sub?: ReactNode;
  tone?: Tone;
  align?: 'center' | 'left';
  children: ReactNode;
}

export function Headline({ id, as: Tag = 'h2', size = 'headline', eyebrow, sub, tone = 'white', align = 'center', children }: Props) {
  const muted = tone === 'night' ? 'text-night-2' : 'text-ink-2';
  const alignment = align === 'center' ? 'mx-auto text-center' : 'text-left';
  return (
    <div className={`max-w-copy ${alignment}`}>
      {eyebrow && <p className={`mb-3 text-body font-semibold ${muted}`}>{eyebrow}</p>}
      <Tag id={id} className={`${size === 'hero' ? 'text-hero' : 'text-headline'} text-balance`}>
        {children}
      </Tag>
      {sub && (
        <p className={`mt-5 text-lead text-pretty ${muted} ${align === 'center' ? 'mx-auto' : ''} max-w-[42rem]`}>{sub}</p>
      )}
    </div>
  );
}
